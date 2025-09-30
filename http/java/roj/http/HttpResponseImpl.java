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
import roj.util.ArtifactVersion;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/15 11:24
 */
final class HttpResponseImpl implements ChannelHandler, HttpResponse {
	static final String HC_FINISH = "hc:finish";

	private HttpHead head;
	private DynByteBuf data;
	private InputStream is;

	private Lock lock;
	private Condition hasData;

	static final byte READY = 0, HEAD = 1, FAIL = 2, SUCCESS = 3;
	volatile byte state;

	ChannelCtx ctx1;
	private boolean async;
	private Throwable ex;

	private Consumer<HttpResponse> callback;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		reset();
		ctx1 = ctx;
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

			if (state != SUCCESS || ctx.postEvent(HC_FINISH).getResult() == Event.RESULT_DEFAULT) {
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
	boolean retain(HttpRequest http, HttpResponseImpl shc) {
		assert state == HttpResponseImpl.SUCCESS;
		ctx1.replaceSelf(shc);
		try {
			ChannelCtx ctx = findOwner(ctx1);
			var handler = (ChannelHandler) http;
			ctx.replaceSelf(handler);
			handler.channelOpened(ctx);
			return true;
		} catch (Exception e) {
			shc.ex = e;
			shc.finish(ctx1, false);
			return false;
		}
	}
	@NotNull
	static ChannelCtx findOwner(ChannelCtx ctx) {
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
		ctx1 = null;
		ex = null;
		async = false;
		callback = null;
	}

	@Override public HttpRequest request() {return ctx1.prev(HttpRequest.class);}

	@Override public int statusCode() throws IOException {return headers().statusCode();}
	@Override public ArtifactVersion version() throws IOException {return headers().version();}
	@Override
	@UnmodifiableView
	public HttpHead headers() throws IOException {
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

	@Override public boolean isSuccess() { return state == SUCCESS; }
	@Override public boolean isDone() { return state >= FAIL; }
	@Override
	public synchronized void awaitCompletion() throws InterruptedException {
		while(state < FAIL) wait();
	}
	@Override
	public synchronized void onCompletion(Consumer<HttpResponse> o) throws IOException {
		ensureOpen();
		callback = o;
		if (state >= HEAD) o.accept(this);
	}
	@Override
	public void disconnect() throws IOException {
		ChannelCtx ctx = ctx1;
		if (ctx != null) ctx.channel().closeGracefully();
	}

	@Override
	public @Nullable Throwable exception() {return ex;}

	@Override
	public synchronized ByteList bytes() throws IOException {
		if (async) throw new IllegalStateException("async handler active");
		if (is != null) throw new IllegalStateException("stream active");

		try {
			awaitCompletion();
		} catch (InterruptedException e) {
			disconnect();
			throw IOUtil.rethrowAsIOException(e);
		}

		ensureOpen();
		return (ByteList) data;
	}
	@Override
	public String text(Charset charset) throws IOException {
		try (TextReader sr = new TextReader(stream(), charset)) {
			return IOUtil.read(sr);
		}
	}
	@Override
	public synchronized void pipe(ChannelHandler h) throws IOException {
		if (is != null) throw new IllegalStateException("stream active");
		ensureOpen();

		ctx1.channel().remove("async_handler");
		ctx1.channel().addLast("async_handler", h);
		ChannelCtx ctx = ctx1.channel().handler("async_handler");

		lock.lock();
		try {
			h.channelRead(ctx, data);
		} finally {
			lock.unlock();
		}
	}
	@Override
	public synchronized InputStream stream() {
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

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("HttpClient{<");
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