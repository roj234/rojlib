package roj.http;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.text.TextReader;
import roj.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/15 11:24
 */
final class HttpResponseImpl implements ChannelHandler, HttpResponse {
	// 经验值, 未调用byte()时的缓冲区最大大小
	private static final int MAX_BUFFER_SIZE = 32768;
	static final String HC_FINISH = "hc:finish";

	private HttpHead head;
	private DynByteBuf buffer;
	private InputStream inputStream;

	private final Object streamLock = new Object();

	static final byte READY = 0, HEAD = 1, FAIL = 2, SUCCESS = 3;
	volatile byte state;

	ChannelCtx ctx1;
	private volatile boolean needGetAll;
	private Throwable ex;

	private Consumer<HttpResponse> callback;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		reset();
		ctx1 = ctx;
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

		if (ctx.next() != null) {
			if (buffer.isReadable()) {
				buffer.compact().put(in);
				in.rIndex = in.wIndex();

				ctx.channelRead(buffer);
			} else {
				buffer.clear();
				ctx.channelRead(in);
			}
		} else {
			synchronized (streamLock) {
				buffer.compact().put(in);
				in.rIndex = in.wIndex();
				streamLock.notifyAll();

				if (!needGetAll && buffer.readableBytes() >= MAX_BUFFER_SIZE) {
					ctx.readInactive();
				}
			}
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

		synchronized (streamLock) {
			streamLock.notifyAll();
		}

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
		if (buffer != null) buffer.release();
		buffer = new ByteList();
		inputStream = null;
		ctx1 = null;
		ex = null;
		callback = null;
	}

	@Override public HttpRequest request() {return ctx1.prev(HttpRequest.class);}

	@Override public int statusCode() throws IOException {return headers().statusCode();}
	@Override public ArtifactVersion version() throws IOException {return headers().version();}
	@Override public HttpHead headers() throws IOException {
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
	public synchronized void onReadyStateChange(Consumer<HttpResponse> handler) throws IOException {
		ensureOpen();
		callback = handler;
		if (state >= HEAD) handler.accept(this);
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
		ensureOpen();

		if (ctx1.next() != null) throw new IllegalStateException("async handler active");
		if (inputStream != null) throw new IllegalStateException("stream active");
		long len = headers().getContentLength();
		if (len > ArrayCache.MAX_ARRAY_SIZE) throw new OutOfMemoryError("Content length("+len+") > MAX_ARRAY_SIZE");

		needGetAll = true;
		ctx1.readActive();

		try {
			awaitCompletion();
		} catch (InterruptedException e) {
			disconnect();
			throw IOUtil.rethrowAsIOException(e);
		}

		ensureOpen();
		return (ByteList) buffer;
	}
	@Override
	public String text(Charset charset) throws IOException {
		try (var reader = new TextReader(stream(), charset)) {
			return IOUtil.read(reader);
		}
	}
	@Override
	public synchronized void pipe(ChannelHandler h) throws IOException {
		ensureOpen();
		if (inputStream != null) throw new IllegalStateException("stream active");

		var ch = ctx1.channel();
		ch.addLast("http:asyncResponse", h);
		ch.readActive();
	}
	@Override
	public synchronized InputStream stream() throws IOException {
		ensureOpen();
		if (inputStream != null) return inputStream;
		if (ctx1.next() != null) throw new IllegalStateException("async handler active");

		return inputStream = new MBInputStream() {
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				ArrayUtil.checkRange(b, off, len);
				if (len == 0) return 0;

				var buf = buffer;
				synchronized (streamLock) {
					while (!buf.isReadable()) {
						ensureOpen();
						if (state >= FAIL) return -1;

						try {
							streamLock.wait();
						} catch (InterruptedException e) {
							close();
							throw IOUtil.rethrowAsIOException(e);
						}
					}

					int read = Math.min(buf.readableBytes(), len);
					buf.readFully(b, off, read);

					if (buf.readableBytes() < MAX_BUFFER_SIZE && state < FAIL) {
						ctx1.readActive();
					}

					return read;
				}
			}

			@Override
			public int available() {
				int len = buffer.readableBytes();
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
		if (inputStream != null) sb.append(", streaming");
		return sb.append('}').toString();
	}
}