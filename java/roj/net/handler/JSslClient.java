package roj.net.handler;

import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
@Deprecated
public class JSslClient extends PacketMerger {
	public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

	private SSLEngine engine;
	private SSLEngineResult.HandshakeStatus status;

	private final ByteBuffer[] tmp = new ByteBuffer[1];
	private int maxRcv, maxSnd;

	public JSslClient() {
		super();
		maxSnd = maxRcv = 512;
	}

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		try {
			engine = SSLContext.getDefault().createSSLEngine();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		engine.setNeedClientAuth(false);
		engine.setUseClientMode(true);
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		engine.beginHandshake();
		status = engine.getHandshakeStatus();
		if (status != SSLEngineResult.HandshakeStatus.FINISHED) handshake(ctx, EMPTY);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (status != SSLEngineResult.HandshakeStatus.FINISHED) throw new IllegalStateException("Not handshake");

		DynByteBuf in = (DynByteBuf) msg;
		ByteBuffer b = in.nioBuffer();
		b.limit(in.wIndex()).position(in.rIndex);
		tmp[0] = b;

		try {
			channelWrite0(ctx);
		} finally {
			tmp[0] = null;
			in.rIndex = b.position();
		}
	}

	private SSLEngineResult channelWrite0(ChannelCtx ctx) throws IOException {
		ByteList out = (ByteList) ctx.allocate(false, maxSnd);
		ByteBuffer buf = out.nioBuffer();

		try {
			while (true) {
				buf.clear();

				SSLEngineResult req = engine.wrap(tmp, buf);
				switch (req.getStatus()) {
					case BUFFER_UNDERFLOW: return req;
					case BUFFER_OVERFLOW:
						out = (ByteList) ctx.alloc().expand(out, out.capacity());
						maxSnd = out.capacity();
						buf = out.nioBuffer();
						break;
					case CLOSED:
						ctx.close();
						return req;
					case OK:
						if (buf.position() > 0) {
							out.wIndex(buf.position());
							ctx.channelWrite(out);
						}

						if (!tmp[0].hasRemaining()) return req;
						break;
				}
			}
		} finally {
			BufferPool.reserve(out);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		ByteBuffer buf = in.nioBuffer();

		if (status != SSLEngineResult.HandshakeStatus.FINISHED) {
			handshake(ctx, buf);
			in.rIndex = buf.position();

			if (status != SSLEngineResult.HandshakeStatus.FINISHED) return;
			if (!in.isReadable()) return;
		}

		try {
			channelRead0(ctx, buf);
		} finally {
			tmp[0] = null;
		}
		in.rIndex = buf.position();
	}

	private SSLEngineResult channelRead0(ChannelCtx ctx, ByteBuffer buf) throws IOException {
		ByteList out = (ByteList) ctx.allocate(false, maxRcv);
		tmp[0] = out.nioBuffer();

		try {
			while (true) {
				tmp[0].clear();

				SSLEngineResult req = engine.unwrap(buf, tmp);
				switch (req.getStatus()) {
					case BUFFER_UNDERFLOW: return req;
					case BUFFER_OVERFLOW:
						out = (ByteList) ctx.alloc().expand(out, out.capacity());
						maxRcv = out.capacity();
						tmp[0] = out.nioBuffer();
						break;
					case CLOSED:
						ctx.close();
						return req;
					case OK:
						if (tmp[0].position() > 0) {
							out.rIndex = 0;
							out.wIndex(tmp[0].position());
							mergedRead(ctx, out);
						}

						if (!buf.hasRemaining() || req.bytesProduced() == 0) return req;
						break;
				}
			}
		} finally {
			BufferPool.reserve(out);
		}
	}

	@SuppressWarnings("fallthrough")
	private void handshake(ChannelCtx ctx, ByteBuffer rcv) throws IOException {
		while (true) {
			switch (status) {
				case NEED_TASK: status = doTasks(); break;
				case NEED_UNWRAP:
					SSLEngineResult r = channelRead0(ctx, rcv);

					status = r.getHandshakeStatus();

					switch (r.getStatus()) {
						case OK: continue;
						case BUFFER_UNDERFLOW: return;
						default: throw new SSLException(r.getStatus() + " state during handshake");
					}
				case NEED_WRAP:
					tmp[0] = EMPTY;
					r = channelWrite0(ctx);

					status = r.getHandshakeStatus();
					if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
						status = doTasks();
					}

					if (r.getStatus() != SSLEngineResult.Status.OK) throw new SSLException(r.getStatus() + " state during handshake");
					break;
				case FINISHED:
					ctx.channelOpened();
					return;
				default: throw new SSLException("Invalid Handshaking State" + status);
			}
		}
	}

	private SSLEngineResult.HandshakeStatus doTasks() {
		Runnable r;
		while ((r = engine.getDelegatedTask()) != null) {
			r.run();
		}
		return engine.getHandshakeStatus();
	}
}