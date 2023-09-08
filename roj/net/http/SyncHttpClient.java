package roj.net.http;

import org.jetbrains.annotations.UnmodifiableView;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.text.TextReader;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/15 0015 11:24
 */
public class SyncHttpClient implements ChannelHandler {
	public static final NamespaceKey SHC_CLOSE_CHECK = new NamespaceKey("shc:finish");

	private HttpHead head;
	private DynByteBuf data;
	private InputStream is;

	private final ReentrantLock lock = new ReentrantLock(true);
	private final Condition hasData = lock.newCondition();

	private static final byte READY = 0, HEAD = 1, FAIL = 2, SUCCESS = 3;
	private volatile byte state;

	private ChannelCtx o;
	private boolean async;
	private Throwable ex;

	private Consumer<SyncHttpClient> callback;

	private List<Map.Entry<HttpRequest, SyncHttpClient>> _queue = Collections.emptyList();

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		reset();
		o = ctx;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		head = ((HttpRequest) findHandler(ctx).handler()).response();
		synchronized (this) {
			state = HEAD;
			notifyAll();
		}
		if (callback != null) callback.accept(this);

		ctx.channelOpened();
	}

	@Nonnull
	private ChannelCtx findHandler(ChannelCtx ctx) {
		ChannelCtx ctx1 = ctx.prev();
		while (!(ctx1.handler() instanceof HttpRequest)) {
			ctx1 = ctx1.prev();
		}
		return ctx1;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		if (!in.isReadable()) return;


		lock.lock();
		try {
			data.compact().put(in);
			if (async) ctx.channelRead(data);
			hasData.signalAll();
		} finally {
			lock.unlock();
			in.rIndex = in.wIndex();
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id == HttpRequest.DOWNLOAD_EOF) {
			finish(ctx, (boolean) event.getData());

			if (ctx.postEvent(SHC_CLOSE_CHECK).getResult() == Event.RESULT_DEFAULT) ctx.channel().closeGracefully();
		}
	}
	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable e) throws Exception {
		if (ex == null) ex = e;
		ctx.exceptionCaught(e);
	}
	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException { finish(ctx, false); }

	private void finish(ChannelCtx ctx, boolean ok) {
		synchronized (this) {
			if (state < FAIL) {
				if (ok) {
					state = SUCCESS;

					if (!_queue.isEmpty()) {
						Map.Entry<HttpRequest, SyncHttpClient> entry = _queue.remove(0);

						SyncHttpClient shc = entry.getValue();
						if (shc._queue.isEmpty()) shc._queue = _queue;
						else shc._queue.addAll(0, _queue);

						execute(entry.getKey(), shc);
					}
				} else {
					state = FAIL;
					if (ex == null) ex = new Exception();

					// 手动调用queue才可能在SUCCESS前
					for (int i = 0; i < _queue.size(); i++) {
						_queue.get(i).getValue().finish(ctx, false);
					}
				}
			}
			notifyAll();
		}

		lock.lock();
		hasData.signalAll();
		lock.unlock();

		if (callback != null) callback.accept(this);
	}

	public SyncHttpClient queue(HttpRequest request, SyncHttpClient client) {
		ChannelCtx ctx = o;
		if (ctx == null) return null;

		boolean locked = ctx.channel().lock().tryLock();
		try {
			if (!ctx.isOpen() || !ctx.isInputOpen() || !ctx.isOutputOpen()) return null;

			synchronized (this) {
				if (state < FAIL) {
					if (_queue.isEmpty()) _queue = new SimpleList<>(4);
					if (_queue.size() >= 4) return null;
					_queue.add(new AbstractMap.SimpleImmutableEntry<>(request, client));
					return this;
				}
			}

			if (!locked) {
				ctx.channel().lock().lock();
				locked = true;
			}

			execute(request, client);
		} finally {
			if (locked) ctx.channel().lock().unlock();
		}

		return client;
	}

	private void execute(HttpRequest http, SyncHttpClient shc) {
		o.replaceSelf(shc);
		try {
			ChannelCtx ctx = findHandler(o);
			ChannelHandler handler = (ChannelHandler) http;
			ctx.replaceSelf(handler);
			handler.channelOpened(ctx);
		} catch (Exception e) {
			shc.ex = e;
			shc.finish(o, false);
		}
	}

	// not use again now
	void reset() {
		state = READY;
		head = null;
		data = new ByteList();
		is = null;
		o = null;
		ex = null;
		async = false;
		callback = null;
	}

	@UnmodifiableView
	public HttpHead head() throws IOException {
		try {
			synchronized (this) {
				while(state < HEAD) wait();
			}
		} catch (InterruptedException e) {
			disconnect();
			throw new ClosedByInterruptException();
		}
		if (state == FAIL) throw new IOException("请求失败", ex);
		return head;
	}
	public boolean isSuccess() { return state == SUCCESS; }
	public boolean isDone() { return state >= FAIL; }

	public synchronized ByteList bytes() throws IOException {
		if (async) throw new IllegalStateException("async handler active");
		if (is != null) throw new IllegalStateException("stream active");

		try {
			waitFor();
		} catch (InterruptedException e) {
			disconnect();
			throw new ClosedByInterruptException();
		}

		if (state == FAIL) throw new IOException("请求失败", ex);
		return (ByteList) data;
	}
	public String utf() throws IOException { return str(StandardCharsets.UTF_8); }
	public String str() throws IOException {
		HttpHead head = head();
		String charset = head.getFieldValue("content-type", "charset");
		return str(charset == null ? null : Charset.forName(charset));
	}
	public String str(Charset charset) throws IOException {
		try (TextReader sr = new TextReader(stream(), charset)) {
			return IOUtil.read(sr);
		}
	}
	public synchronized void async(ChannelHandler h) throws IOException {
		if (is != null) throw new IllegalStateException("stream active");
		if (state == FAIL) throw new IOException("请求失败", ex);

		o.channel().remove("async_handler");
		o.channel().addLast("async_handler", h);
		ChannelCtx ctx = o.channel().handler("async_handler");

		lock.lock();
		try {
			h.channelRead(ctx, data);
		} finally {
			lock.unlock();
		}
	}
	public synchronized InputStream stream() throws IOException {
		if (is != null) return is;

		if (async) throw new IllegalStateException("async handler active");
		return is = new InputStream() {
			byte[] sb;

			@Override
			public int read() {
				if (sb == null) sb = new byte[1];

				int v = read(sb, 0, 1);
				if (v < 0) return v;
				return sb[0]&0xFF;
			}

			@Override
			public int read(byte[] b, int off, int len) {
				if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
				if (len == 0) return 0;

				lock.lock();
				try {
					while (!data.isReadable()) {
						if (state >= FAIL) return -1;

						hasData.awaitUninterruptibly();
					}

					int v = Math.min(data.readableBytes(), len);
					data.read(b, off, v);
					return v;
				} finally {
					lock.unlock();
				}
			}

			@Override
			public int available() {
				int len = data.readableBytes();
				return len == 0 && state >= FAIL ? -1 : len;
			}

			@Override
			public void close() throws IOException { disconnect(); }
		};
	}

	public synchronized void waitFor() throws InterruptedException {
		while(state < FAIL) wait();
	}

	public synchronized void await(Consumer<SyncHttpClient> o) throws IOException {
		if (state == FAIL) throw new IOException("请求失败", ex);
		callback = o;
	}

	public void disconnect() throws IOException {
		ChannelCtx o1 = o;
		if (o1 != null) o1.channel().closeGracefully();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("SyncHttpClient{<");
		switch (state) {
			case READY: sb.append("ready"); break;
			case HEAD: sb.append("head"); break;
			case FAIL: sb.append("fail"); break;
			case SUCCESS: sb.append("success"); break;
		}
		sb.append('>');
		if (is != null) sb.append(", streaming");
		if (!_queue.isEmpty()) sb.append(", ").append(_queue.size()).append(" queued");
		return sb.append('}').toString();
	}
}
