package roj.plugins.http.php;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.ClientLaunch;
import roj.net.ch.MyChannel;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.function.Consumer;

import static roj.reflect.ReflectionUtils.u;

/**
 * 来源：nginx抓包分析
 * 网上的教程都是扯淡，什么8字节对齐什么每个param一个包都是骗人的
 * @author Roj234
 * @since 2024/7/1 0001 20:12
 */
public final class fcgiConnection implements ChannelHandler, Consumer<MyChannel> {
	private static final int
		FCGI_BEGIN_REQUEST = 1, FCGI_ABORT_REQUEST = 2, FCGI_END_REQUEST/*RECV*/ = 3,
		FCGI_PARAMS = 4,
		FCGI_STDIN = 5, FCGI_STDOUT/*RECV*/ = 6, FCGI_STDERR/*RECV*/ = 7,
		FCGI_DATA = 8, FCGI_GET_VALUES = 9, FCGI_GET_VALUES_RESULT/*RECV*/ = 11,
		FCGI_UNKNOWN_TYPE/*RECV*/ = 11;

	private static final int FCGI_RESPONDER = 1, FCGI_AUTHORIZER = 2, FCGI_FILTER = 3;

	private static final int
		FCGI_REQUEST_COMPLETE = 0,
		FCGI_CANT_MPX_CONN    = 1,
		FCGI_OVERLOADED       = 2,
		FCGI_UNKNOWN_ROLE     = 3;

	final fcgiManager manager;
	final int port;

	private ChannelCtx ctx;
	private Throwable closeEx, abortEx;
	private boolean opened;

	int abort;
	long idleTime;

	private final Object lock = new Object();
	private volatile fcgiResponse activeResponse;
	private static final long OFF = ReflectionUtils.fieldOffset(fcgiConnection.class, "activeResponse");

	fcgiConnection(fcgiManager manager, int port) throws IOException {
		this.manager = manager;
		this.port = port;
		ClientLaunch.tcp().connect(InetAddress.getLocalHost(), port).timeout(500).initializator(this).launch();
	}

	final int attach(fcgiResponse resp, Map<String, String> param) throws Exception {
		if (!ctx.isOpen()) return -1;

		if (!opened) {
			synchronized (lock) {lock.wait();}
			if (closeEx != null) Helpers.athrow(closeEx);
			if (!(opened & ctx.isOpen())) return -1;
		}

		if (!u.compareAndSwapObject(this, OFF, null, resp)) return 0;
		abort = -1;
		abortEx = null;

		ByteList buf = IOUtil.getSharedByteBuf();
		// typedef struct {
		//    unsigned byte version;
		//    unsigned byte type;
		//    unsigned char requestId;
		//    unsigned char contentLength;
		//    unsigned byte paddingLength;
		//    unsigned byte reserved;
		//} FCGI_Header;
		buf.putLong(/*FCGI_BEGIN_REQUEST*/0x01_01_0001_0008_0000L);

		// typedef struct {
		//    unsigned char role;
		//    unsigned byte flag;
		//    unsigned byte reserved[5];
		//} FCGI_BeginRequestBody;
		buf.putLong(/*FCGI_RESPONDER + keep-alive*/0x0001_01_0000000000L);

		buf.putLong(/*FCGI_PARAMS*/0x01_04_0001_0000_0000L);
		int offset = buf.wIndex();

		ByteList b = new ByteList();
		for (Map.Entry<String, String> entry : param.entrySet()) {
			b.clear();
			b.putUTFData(entry.getKey());

			int keyLen = b.length();
			if (keyLen > 127) buf.putInt(0x80000000|keyLen);
			else buf.put(keyLen);

			b.putUTFData(entry.getValue());

			int valLen = b.length() - keyLen;
			if (valLen > 127) buf.putInt(0x80000000|valLen);
			else buf.put(valLen);

			buf.put(b);
		}
		b._free();

		int len = buf.wIndex() - offset;
		buf.putShort(offset-4, len);

		// end of params
		buf.putLong(/*FCGI_PARAMS*/0x01_04_0001_0000_0000L);

		ctx.channel().fireChannelWrite(buf);

		resp.conn = this;
		resp.opened();
		return 1;
	}

	public ChannelCtx ctx() {return ctx;}
	public void handlerAdded(ChannelCtx ctx) {this.ctx = ctx;}
	public void channelOpened(ChannelCtx ctx) {opened = true;synchronized (lock) {lock.notifyAll();}}
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {this.closeEx = ex; ctx.close();}
	public void channelClosed(ChannelCtx ctx) {
		if (closeEx == null) closeEx = new Exception();

		try {
			var response = activeResponse;
			if (response != null) response.fail(closeEx);
		} catch (Exception ignored) {}

		synchronized (lock) {lock.notifyAll();}
		manager.connectionClosed(this);
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		idleTime = System.currentTimeMillis();

		var ib = (DynByteBuf) msg;
		while (true) {
			if (ib.readableBytes() < 8 || ib.readableBytes() < ib.readUnsignedShort(ib.rIndex+4)+8) return;

			int start = ib.rIndex;
			int type = ib.readUnsignedShort();
			int id = ib.readUnsignedShort();
			int length = ib.readUnsignedShort();
			int padding = ib.readUnsignedByte();
			ib.rIndex++;

			var resp = activeResponse;
			if (type == 0x0103) {
				if (resp != null) {
					resp.setEof();
					ctx.readActive();
					abort = -1;
					activeResponse = null;
					manager.requestFinished(this);
				}

				int exitCode = ib.readInt();
				if (exitCode != 0) {
					fcgiManager.LOGGER.warn("服务器{}; 请求#{}; ExitCode={}", ctx.remoteAddress(), id, exitCode);
				}

				int status = ib.readUnsignedByte();
				if (status != FCGI_REQUEST_COMPLETE) {
					fcgiManager.LOGGER.warn("服务器{}; 请求#{}; ProtocolState={}", ctx.remoteAddress(), id, switch (status) {
						case FCGI_CANT_MPX_CONN -> "FCGI_CANT_MPX_CONN";
						case FCGI_OVERLOADED -> "FCGI_OVERLOADED";
						case FCGI_UNKNOWN_ROLE -> "FCGI_UNKNOWN_ROLE";
						default -> "Unknown status #"+status;
					});
				}
				ib.rIndex += length - 5;
			} else if (resp == null) {
				fcgiManager.LOGGER.warn("服务器{}; 请求#{}; CGI写入不存在的请求 {}", ctx.remoteAddress(), id, type);
				ctx.close();
				return;
			} else {
				if (abort >= 0) {
					if ((abort += length) > 262144) {
						fcgiManager.LOGGER.debug("服务器{}; 请求#{}; 中止的请求仍有大量数据({})，关闭连接", ctx.remoteAddress(), id, abort);
						closeEx = abortEx;
						ctx.close();
					}
					ib.rIndex += length;
				} else {
					DynByteBuf data = ib.slice(length);
					try {
						if (!resp.isEof() && !resp.offer(data)) {
							fcgiManager.LOGGER.debug("服务器{}; 请求#{}; 接收缓冲已满", ctx.remoteAddress(), id);

							ib.rIndex = start;
							ctx.readInactive();
							return;
						}
					} catch (Exception e) {
						ctx.readActive();
						abortEx = e;
						fcgiManager.LOGGER.warn("服务器{}; 请求#{}; 本地连接已关闭", e, ctx.remoteAddress(), id);
						abort = 0;
					}
				}
			}
			ib.rIndex += padding;
		}
	}

	public ChannelCtx ensureOpen() {
		if (closeEx != null) Helpers.athrow(closeEx);
		return ctx;
	}

	@Override
	public void channelFlushed(ChannelCtx ctx) {
		if (remote != null) {
			remote.readActive();
			remote = null;
		}
	}

	@Override
	public void accept(MyChannel x) {x.addLast("fastcgi_handler", this);}

	private ChannelCtx remote;
	void checkFlushing(ChannelCtx ctx1) {
		if (ctx.isFlushing()) {
			ctx.pauseAndFlush();

			remote = ctx1;
			ctx1.readInactive();
		}
	}

	void requestFinished(fcgiResponse response) {
		//potential release before setEof()
		if (u.compareAndSwapObject(this, OFF, response, null)) {
			abort = 0;
		}
	}
}