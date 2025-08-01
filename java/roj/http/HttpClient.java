package roj.http;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.text.TextReader;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/15 11:24
 */
public final class HttpClient implements ChannelHandler {
	public static final String SHC_FINISH = "shc:finish";

	private HttpHead head;
	private DynByteBuf data;
	private InputStream is;

	private Lock lock;
	private Condition hasData;

	static final byte READY = 0, HEAD = 1, FAIL = 2, SUCCESS = 3;
	volatile byte state;

	ChannelCtx o;
	private boolean async;
	private Throwable ex;

	private Consumer<HttpClient> callback;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		reset();
		o = ctx;
		lock = ctx.channel().lock();
		hasData = lock.newCondition();
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		head = ctx.prev(HttpRequest.class).response();
		synchronized (this) {
			state = HEAD;
			notifyAll();
		}
		if (callback != null) callback.accept(this);

		ctx.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		if (!in.isReadable()) return;

		try {
			data.compact().put(in);
			if (async) ctx.channelRead(data);
			hasData.signalAll();
		} finally {
			in.rIndex = in.wIndex();
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(HttpRequest.DOWNLOAD_EOF)) {
			finish(ctx, (boolean) event.getData());

			if (state != SUCCESS || ctx.postEvent(SHC_FINISH).getResult() == Event.RESULT_DEFAULT) {
				try {
					ctx.channel().closeGracefully();
				} catch (IOException ignored) {
					ctx.channel().close();
				}
			}
		}
	}
	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable e) throws Exception {
		if (ex == null) ex = e;
		ctx.close();
		//ctx.exceptionCaught(e);
	}
	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException { finish(ctx, false); }

	private void ensureOpen() throws IOException {
		if (state == FAIL) throw new IOException("请求失败: "+this, ex);
	}

	private void finish(ChannelCtx ctx, boolean ok) {
		synchronized (this) {
			if (state < FAIL) {
				if (ok) {
					state = SUCCESS;
				} else {
					state = FAIL;
					if (ex == null) ex = new IllegalStateException("未预料的连接关闭");
				}
			}
			notifyAll();
		}

		lock.lock();
		hasData.signalAll();
		lock.unlock();

		if (callback != null) callback.accept(this);
	}

	/**
	 * try reuse connection
	 */
	boolean retain(HttpRequest http, HttpClient shc) {
		assert state == HttpClient.SUCCESS;
		o.replaceSelf(shc);
		try {
			ChannelCtx ctx = findHandler(o);
			var handler = (ChannelHandler) http;
			ctx.replaceSelf(handler);
			handler.channelOpened(ctx);
			return true;
		} catch (Exception e) {
			shc.ex = e;
			shc.finish(o, false);
			return false;
		}
	}
	@NotNull
	static ChannelCtx findHandler(ChannelCtx ctx) {
		ChannelCtx ctx1 = ctx.prev();
		while (!(ctx1.handler() instanceof HttpRequest)) {
			ctx1 = ctx1.prev();
		}
		return ctx1;
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
			throw IOUtil.rethrowAsIOException(e);
		}
		ensureOpen();
		return head;
	}
	public boolean isSuccess() { return state == SUCCESS; }
	public boolean isDone() { return state >= FAIL; }

	public @Nullable Throwable exception() {return ex;}

	public synchronized ByteList bytes() throws IOException {
		if (async) throw new IllegalStateException("async handler active");
		if (is != null) throw new IllegalStateException("stream active");

		try {
			waitFor();
		} catch (InterruptedException e) {
			disconnect();
			throw IOUtil.rethrowAsIOException(e);
		}

		ensureOpen();
		return (ByteList) data;
	}
	public String utf() throws IOException { return str(StandardCharsets.UTF_8); }
	public String str() throws IOException {
		HttpHead head = head();
		String charset = head.getHeaderValue("content-type", "charset");
		return str(charset == null ? null : Charset.forName(charset));
	}
	public String str(Charset charset) throws IOException {
		try (TextReader sr = new TextReader(stream(), charset)) {
			return IOUtil.read(sr);
		}
	}
	public synchronized void async(ChannelHandler h) throws IOException {
		if (is != null) throw new IllegalStateException("stream active");
		ensureOpen();

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
		return is = new MBInputStream() {
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
				if (len == 0) return 0;

				lock.lock();
				try {
					while (!data.isReadable()) {
						ensureOpen();
						if (state >= FAIL) return -1;

						hasData.awaitUninterruptibly();
					}

					int v = Math.min(data.readableBytes(), len);
					data.readFully(b, off, v);
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

	public synchronized void await(Consumer<HttpClient> o) throws IOException {
		ensureOpen();
		callback = o;
		if (state >= HEAD) o.accept(this);
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
		return sb.append('}').toString();
	}
}