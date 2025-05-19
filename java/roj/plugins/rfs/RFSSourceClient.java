package roj.plugins.rfs;

import roj.collect.RingBuffer;
import roj.concurrent.Promise;
import roj.concurrent.TaskPool;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.ClientLaunch;
import roj.net.MyChannel;
import roj.net.handler.MSSCrypto;
import roj.net.handler.Timeout;
import roj.net.mss.MSSContext;
import roj.plugins.rfs.proto.ClientPacket;
import roj.plugins.rfs.proto.Packet;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static roj.reflect.Unaligned.U;

/**
 * 远程文件系统，Source包装客户端
 * var client = new RFSSourceClient("");
 * client.connect(new InetSocketAddress("", 0)).then((rfsClient, next) -> {
 * 		try {
 * 			Source remoteFile = rfsClient.getRemoteSource("", false);
 *			remoteFile.close();
 * 		} catch (IOException e) {
 * 			next.reject(e);
 * 		}
 * });
 * @author Roj234
 * @since 2024/10/27 0:34
 */
public class RFSSourceClient implements ChannelHandler, Consumer<MyChannel> {
	private static final Logger LOGGER = Logger.getLogger();
	private static final MSSContext context = new MSSContext().setALPN("RFS");
	private static final ChannelHandler PACKET_SERIALIZER = Packet.SERIALIZER.client();

	private String accessToken;
	public RFSSourceClient(String accessToken) {this.accessToken = accessToken;}

	public static void main(String[] args) throws IOException {
		HighResolutionTimer.runThis();
	}

	public Source getRemoteSource(String path, boolean writable) throws IOException {
		var rpc = new RPCTask();
		rpc.type = 0;
		rpc.path = path;
		rpc.handle = writable ? Packet.OpenHandle.OPEN_WRITE : Packet.OpenHandle.OPEN_READ;
		executeSync(rpc);
		return new RemoteSource(rpc.handle, rpc.p1);
	}

	private Promise.Callback callback;
	@SuppressWarnings("unchecked")
	public Promise<RFSSourceClient> connect(InetSocketAddress address) throws IOException {
		ClientLaunch.tcp().connect(address).initializator(this).launch();
		return (Promise<RFSSourceClient>) (callback = Promise.sync());
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("mss", new MSSCrypto(context.clientEngine()))
				.addLast("timeout", new Timeout(60000, 5000))
				.addLast("packet", PACKET_SERIALIZER)
				.addLast("client", this);
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		((ClientPacket) msg).handle(this);
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		LOGGER.info("Connected");
		this.ctx = ctx;
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		LOGGER.info("Disconnected");
		if (callback != null) {
			callback.reject(null);
		}
	}

	private ChannelCtx ctx;
	private ReentrantLock lock = new ReentrantLock();
	private RingBuffer<RPCTask> rpcTasks = RingBuffer.unbounded();
	private RPCTask rpc;
	private static final int MAX_FILE_DATA = 0x10000;
	private static final long STATE_OFFSET = ReflectionUtils.fieldOffset(RPCTask.class, "state");

	@Override
	public void channelTick(ChannelCtx ctx) throws Exception {
		if ((rpc == null) && !rpcTasks.isEmpty()) {
			if (lock.tryLock()) {
				try {
					rpc = rpcTasks.pollFirst();
				} finally {
					lock.unlock();
				}
			}
			if (rpc != null) {
				if (!U.compareAndSwapInt(rpc, STATE_OFFSET, 0, 1)) {
					rpc = null;
					return;
				}
				switch (rpc.type) {
					case 0 -> ctx.channelWrite(new Packet.OpenHandle(rpc.path, rpc.handle)); // handle
					case 1 -> ctx.channelWrite(new Packet.FileHandleOp(rpc.handle, Packet.FileHandleOp.META_READ, rpc.p0.writableBytes())); // readcount
					case 2 -> ctx.channelWrite(new Packet.FileData(rpc.handle, buf -> buf.put(rpc.p0.slice(Math.min(rpc.p0.readableBytes(), MAX_FILE_DATA))))); // writecount
					case 3 -> ctx.channelWrite(new Packet.FileHandleOp(rpc.handle, Packet.FileHandleOp.META_POSITION, rpc.p1));
					case 4 -> ctx.channelWrite(new Packet.FileHandleOp(rpc.handle, Packet.FileHandleOp.META_LENGTH, rpc.p1));
				}
			}
		}
	}

	public void onFail(String message) throws IOException {
		if (rpc == null) return;
		if (message.isEmpty()) {
			if (rpc.type <= 1) rpc.done("illegal response for type="+rpc.type);
			else if (rpc.type != 2 || rpc.p0.readableBytes() == 0) {
				if (rpc.type == 5) {
					if (callback != null) {
						callback.resolveOn(this, TaskPool.Common());
					}
				}
				rpc.done(null);
			} else if (rpc.preDone()) {
				ctx.channelWrite(new Packet.FileData(rpc.handle, buf -> buf.put(rpc.p0.slice(Math.min(rpc.p0.readableBytes(), MAX_FILE_DATA)))));
				return;
            }
		} else {
			LOGGER.warn("Error: " + message);
			rpc.done(message);
		}
		rpc = null;
	}

	public void handleFileMeta(Packet.FileMetadata packet) {

	}

	public void handleDirectoryContent(Packet.DirectoryMeta packet) {

	}

	public void onOpenHandle(Packet.FileHandle packet) {
		if (rpc == null) return;
		if (rpc.type != 0) {
			rpc.done("illegal response");
		} else {
			if (packet.handle < 0) {
				rpc.done(packet.error());
			} else if (rpc.preDone()) {
				rpc.handle = packet.handle;
				rpc.p1 = packet.length;
				rpc.done(null);
			}
		}
		rpc = null;
	}

	public void onFileData(Packet.FileData packet) throws IOException {
		if (rpc == null) return;
		if (rpc.type != 1 || rpc.handle != packet.handle) {
			rpc.done("illegal response");
		} else {
			boolean preDone = rpc.preDone();
			if (preDone) {
				rpc.p1 += packet.data.readableBytes();
				rpc.p0.put(packet.data);
			} else {
				ctx.channelWrite(new Packet.FileHandleOp(rpc.handle, Packet.FileHandleOp.CANCEL_FILEDATA, 0L));
			}

			if (!preDone || packet.data.readableBytes() == 0 || rpc.p0.writableBytes() == 0) {
				rpc.done(null);
			} else {
				return;
			}
		}
		rpc = null;

	}

	public void onLogin(String brand) throws IOException {
		LOGGER.warn("{} Server requesting credential for login", brand);
		if (accessToken == null) LOGGER.warn("匿名登录");
		else ctx.channelWrite(new Packet.AccessToken(accessToken));

		rpc = new RPCTask();
		rpc.type = 5;
		rpc.failCallback = e -> IOUtil.closeSilently(ctx.channel());
	}

	static class RPCTask {
		String path;
		int handle;

		int type;
		DynByteBuf p0;
		long p1;

		Exception exception;
		volatile int state;
		Lock lock = new ReentrantLock();
		Condition condition = lock.newCondition();

		Consumer<Exception> failCallback;

		boolean preDone() {
			while (true) {
				var st = state;
				if (st == 2) return true;
				if (st != 1) return false;
				if (U.compareAndSwapInt(this, STATE_OFFSET, 1, 2)) return true;
			}
		}
		void done(String message) {
			if (message != null) {
				exception = new IOException(message);
				if (failCallback != null && !message.equals("cancelled")) failCallback.accept(exception);
			}
			state = 3;
			lock.lock();
			condition.signalAll();
			lock.unlock();
		}

		public void cancel() {
			if (U.getAndSetInt(this, STATE_OFFSET, 3) != 3) {
				done("cancelled");
			}
		}

		public void waitFor() {
			lock.lock();
			try {
				while (state != 3) {
					condition.awaitUninterruptibly();
				}
				if (exception != null) Helpers.athrow(exception);
			} finally {
				lock.unlock();
			}
		}

		public void waitForSend() {
			while (state < 1) {}
		}

		public boolean isFinished() {
			return state == 3;
		}
	}
	private void executeSync(RPCTask rpc) throws IOException {
		execute(rpc);
		rpc.waitFor();
	}

	private void execute(RPCTask rpc) {
		lock.lock();
		try {
			rpcTasks.addLast(rpc);
		} finally {
			lock.unlock();
		}
	}

	public class RemoteSource extends Source implements Consumer<Exception> {
		private final int handle;
		private long offset, length;
		private Throwable ex;

		private long rsOffset;

		private RPCTask rpc;
		private ByteList staging = ByteList.allocate(MAX_FILE_DATA, MAX_FILE_DATA);
		private ByteList nextBuf = ByteList.allocate(MAX_FILE_DATA, MAX_FILE_DATA);

		@Override
		public void accept(Exception e) {ex = e;}

		public RemoteSource(int handle, long length) {
			this.handle = handle;
			this.length = length;
		}

		@Override
		public boolean isBuffered() {return true;}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (ex != null) Helpers.athrow(ex);

			ArrayUtil.checkRange(b, off, len);
			if (len == 0) return 0;

			int remain = len;
			while (true) {
				var immediateReadable = Math.min(staging.readableBytes(), remain);
				if (immediateReadable > 0) {
					staging.readFully(b, off, immediateReadable);
					remain -= immediateReadable;
					if (remain == 0) break;
					off += immediateReadable;
				}

				if (rpc != null) {
					if (!rpc.isFinished()) {
						if (staging.capacity() < 262144) {
							staging = DynByteBuf.allocate(staging.capacity() << 1, staging.capacity() << 1);
						}
						rpc.waitFor();
					}
					// phase 2
					if (rpc.p0.wIndex() == 0) break;
					rsOffset += MAX_FILE_DATA;
				} else {
					rsOffset = offset;
				}

				var curr = staging;
				var next = nextBuf;
				nextBuf = curr;
				staging = next;

				curr.clear();

				// phase 1
				if (rpc != null && rpc.p0.wIndex() < MAX_FILE_DATA) continue;

				rpc = new RPCTask();
				rpc.handle = handle;
				rpc.type = 1;
				rpc.p0 = curr;
				rpc.failCallback = this;
				execute(rpc);
			}

			len -= remain;
			if (len == 0) return -1;
			offset += len;
			return len;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {write(DynByteBuf.wrap(b, off, len));}

		@Override
		public void write(DynByteBuf data) throws IOException {
			if (ex != null) Helpers.athrow(ex);

			if (rsOffset >= 0) {
				seek(offset);
				rsOffset = -1;
			}

			var rpc = new RPCTask();
			rpc.type = 2;
			rpc.p0 = data;
			rpc.failCallback = this;
			executeSync(rpc);

			offset += data.readableBytes();
			if (offset > length) length = offset;
		}

		@Override
		public void seek(long pos) throws IOException {
			if (ex != null) Helpers.athrow(ex);

			var delta = pos - offset;
            if (delta == 0) return;

			offset = pos;
			if (rpc != null) {
				if (delta > 0 ? delta < staging.readableBytes() : staging.rIndex >= -delta) {
					staging.rIndex += delta;
					return;
				}
				//System.out.println("Cannot retract!");
                rpc.cancel();
                rpc = null;
                staging.clear();
				nextBuf.clear();
				rsOffset = -1;
			}

			var rpc = new RPCTask();
			rpc.type = 3;
			rpc.p1 = pos;
			rpc.failCallback = this;
			execute(rpc);
		}

		@Override
		public long position() throws IOException {return offset;}

		@Override
		public void setLength(long length) throws IOException {
			if (ex != null) Helpers.athrow(ex);

			var rpc = new RPCTask();
			rpc.type = 4;
			rpc.p1 = length;
			rpc.failCallback = this;
			execute(rpc);
			this.length = length;
		}

		@Override
		public long length() throws IOException {return length;}

		@Override
		public Source threadSafeCopy() throws IOException {
			return null;
		}

		@Override
		public void moveSelf(long from, long to, long length) throws IOException {
			throw new UnsupportedOperationException();
		}
	}
}
