package roj.net.ch.handler;

import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSEngineClient;
import roj.net.mss.MSSException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

import static roj.util.ByteList.EMPTY;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
public class MSSCipher extends PacketMerger {
	private final MSSEngine engine;
	private boolean sslMode;

	public MSSCipher(MSSEngine engine) {
		super();
		this.engine = engine;
	}

	public MSSCipher() {
		super();
		this.engine = new MSSEngineClient();
	}

	public MSSCipher sslMode() {
		sslMode = true;
		return this;
	}

	public MSSEngine getEngine() { return engine; }

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		handshake(ctx, EMPTY);
		if (engine.isHandshakeDone()) ctx.channelOpened();
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (!ctx.isOutputOpen()) return;

		DynByteBuf in = (DynByteBuf) msg;
		DynByteBuf out = ctx.allocate(false, 1024);
		try {
			do {
				int req = engine.wrap(in, out);
				if (req >= 0) ctx.channelWrite(out);
				else out = BufferPool.expand(out,-req);
			} while (in.isReadable());
		} finally {
			BufferPool.reserve(out);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;

		if (!engine.isHandshakeDone()) {
			handshake(ctx, in);
			if (!engine.isHandshakeDone()) return;

			ctx.channelOpened();
			if (!in.isReadable()) return;
		}

		DynByteBuf out = ctx.allocate(false, 1024);
		try {
			while (true) {
				int req = engine.unwrap(in, out);
				if (req > 0) {
					out = BufferPool.expand(out, req);
				} else if (req == 0) {
					mergedRead(ctx, out);
					out.clear();

					if (!in.isReadable()) return;
				} else {
					return;
				}
			}
		} finally {
			BufferPool.reserve(out);
		}
	}

	private void handshake(ChannelCtx ctx, DynByteBuf rx) throws IOException {
		int req;

		DynByteBuf tx = ctx.allocate(true, 1024);
		try {
			do {
				try {
					req = sslMode ? engine.handshakeSSL(tx, rx) : engine.handshake(tx, rx);
				} catch (MSSException e) {
					if (!engine.isClientMode() && e.code == MSSEngine.ILLEGAL_PACKET) {
						ctx.channelWrite(IOUtil.getSharedByteBuf().putAscii(
							"HTTP/1.1 400 Bad Request\r\n" +
								"Connection: close\r\n" +
								"\r\n" +
								"<h1>This is not a HTTP server</h1>"));
					}
					throw e;
				}

				// overflow
				if (req == MSSEngine.HS_OK) {
					if (tx.wIndex() > 0) {
						ctx.channelWrite(tx);
						tx.clear();
					}
					if (engine.isHandshakeDone()) return;
				} else if (req < 0) {
					return;
				} else {
					tx = BufferPool.expand(tx, req);
				}
			} while (true);
		} finally {
			BufferPool.reserve(tx);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		engine.close();
	}

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		if (ex instanceof MSSException) {
			if (!sslMode) {
				try {
					ByteList ob = IOUtil.getSharedByteBuf();
					((MSSException) ex).notifyRemote(engine, ob);
					ctx.channelWrite(ob);
				} catch (Exception ignored) {}
			}
		}
		ctx.exceptionCaught(ex);
	}
}
