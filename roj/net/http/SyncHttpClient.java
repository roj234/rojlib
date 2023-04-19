package roj.net.http;

import org.jetbrains.annotations.Nullable;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static roj.util.ByteList.EMPTY;

/**
 * @author Roj234
 * @since 2022/10/15 0015 11:24
 */
public class SyncHttpClient implements ChannelHandler {
	public static final NamespaceKey SHC_DONE = new NamespaceKey("shc:finish");

	private DynByteBuf merged = EMPTY;
	private ByteList result;
	private HttpHead head;

	private volatile InputStream is;
	private ReentrantLock pipeLock;
	private Condition hasData;

	private boolean done, success;
	private ChannelCtx o;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		o = ctx;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		head = ((IHttpClient) ctx.channel().handler("HH").handler()).response();
		result = null;
		done = success = false;
		ctx.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		if (!in.isReadable()) return;

		InputStream hin = is;
		if (hin != null) pipeLock.lock();
		try {
			if (merged.capacity() > 0) {
				merged.compact();
				int more = in.readableBytes() - merged.writableBytes();
				if (more > 0) merged = ctx.alloc().expand(merged, more);
			} else merged = ctx.allocate(false, in.readableBytes());

			merged.put(in);
			hasData.signalAll();
		} finally {
			if (hin != null) pipeLock.unlock();
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id == IHttpClient.DOWNLOAD_EOF) {
			pipeLock.lock();
			try {
				result = ByteList.allocate(merged.readableBytes());
				result.put(merged);

				success = true;
				channelClosed(ctx);

				hasData.signalAll();
			} finally {
				pipeLock.unlock();
			}

			Event e = ctx.postEvent(SHC_DONE);
			if (e.getResult() == Event.RESULT_DEFAULT) ctx.close();
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (merged.capacity() > 0) {
			ctx.reserve(merged);
			merged = EMPTY;
		}
		is = null;
		o = null;
		synchronized (this) {
			if (!done) {
				done = true;
				notifyAll();
			}
		}
	}

	@Nullable
	public HttpHead getHead() {
		return head;
	}

	public boolean isSuccess() {
		return success;
	}

	public boolean isDone() {
		return done;
	}

	public ByteList getResult() {
		if (result == null) throw new IllegalStateException();
		return result;
	}

	public String getAsUTF8Str() {
		if (result == null) throw new IllegalStateException();
		return result.readUTF(result.readableBytes());
	}

	public void waitFor() throws InterruptedException {
		synchronized (this) {
			if (!done) {
				wait();
			}
		}
	}

	public synchronized InputStream getInputStream() throws IOException {
		if (result != null) return result.asInputStream();

		if (is != null) return is;

		pipeLock = new ReentrantLock();
		hasData = pipeLock.newCondition();
		return is = new InputStream() {
			byte[] sb;
			DynByteBuf res;

			@Override
			public int read() {
				if (sb == null) sb = new byte[1];

				int v = read(sb, 0, 1);
				if (v < 0) return v;
				return sb[0]&0xFF;
			}

			boolean p2;

			@Override
			public int read(byte[] b, int off, int len) {
				if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
				if (len == 0) return 0;
				if (res != null) {
					int v = res.readableBytes();
					if (v > 0) {
						v = Math.min(v, len);
						res.read(b, off, v);
						return v;
					}
					return -1;
				}

				int v;
				pipeLock.lock();
				while (true) {
					v = merged.readableBytes();
					if (v > 0) {
						v = Math.min(v, len);
						merged.read(b, off, v);
						break;
					} else {
						if (done&success) {
							res = result;
							return read(b, off, len);
						}
						hasData.awaitUninterruptibly();
						if (!merged.isReadable()) {
							if (done&success) {
								res = result;
								return read(b, off, len);
							}
							v = -1;
							break;
						}
					}
				}
				pipeLock.unlock();
				return v;
			}

			@Override
			public void close() throws IOException {
				if (o != null) o.close();
			}
		};
	}

	public void disconnect() throws IOException {
		if (o != null) o.close();
	}
}
