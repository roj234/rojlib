package roj.net.handler;

import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSEngineClient;
import roj.net.mss.MSSException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static roj.util.ByteList.EMPTY;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
public class MSSCipher extends PacketMerger {
	private final MSSEngine engine;

	private static final byte TLS13 = 1;
	private byte flag;

	public MSSCipher(MSSEngine engine) {this.engine = engine;}
	public MSSCipher() {this.engine = new MSSEngineClient();}

	public MSSCipher sslMode() {flag |= TLS13;return this;}
	public MSSEngine getEngine() {return engine;}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		handshake(ctx, EMPTY);
		if (engine.isHandshakeDone()) ctx.channelOpened();
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (!ctx.isOutputOpen()) return;

		var in = (DynByteBuf) msg;
		var out = ctx.allocate(false, 1024);
		try {
			while (in.isReadable()) {
				int req = engine.wrap(in, out);
				if (req >= 0) ctx.channelWrite(out);
				else out = ctx.alloc().expand(out, -req);
			}
		} finally {
			BufferPool.reserve(out);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var in = (DynByteBuf) msg;

		if (!engine.isHandshakeDone()) {
			handshake(ctx, in);
			if (!engine.isHandshakeDone()) return;

			ctx.channelOpened();
			if (!in.isReadable()) return;
		}

		int lim = in.wIndex();
		while (in.isReadable()) {
			var packet = engine.publicReadPacket(in);
			if (packet != 0) break;

			var decoder = engine.getDecoder();

			int size = decoder.engineGetOutputSize(in.readableBytes());
			try(var out = ctx.allocate(in.isDirect(), size)) {
				decoder.cryptFinal(in, out);
				mergedRead(ctx, out);
			} catch (GeneralSecurityException e) {
				throw new MSSException("解密失败:"+e.getMessage());
			} finally {
				in.wIndex(lim);
			}
		}
	}

	private void handshake(ChannelCtx ctx, DynByteBuf rx) throws IOException {
		int req;

		var tx = ctx.allocate(true, 1024);
		try {
			for(;;) {
				req = (flag&TLS13) != 0 ? engine.handshakeTLS13(tx, rx) : engine.handshake(tx, rx);

				// overflow
				if (req == MSSEngine.HS_OK) {
					if (tx.wIndex() > 0) {
						ctx.channelWrite(tx);
						tx.clear();
					}
					if (engine.isHandshakeDone()) return;
				} else if (req < 0) {
					if (req != MSSEngine.HS_PRE_DATA) return;
					ctx.channelRead(tx);
				} else {
					tx = ctx.alloc().expand(tx, req);
				}
			}
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
		if (ex instanceof MSSException e && e.code != MSSEngine.ILLEGAL_PACKET) {
			if ((flag&TLS13) == 0) {
				try {
					ByteList ob = IOUtil.getSharedByteBuf();
					e.notifyRemote(engine, ob);
					ctx.channelWrite(ob);
				} catch (Exception ignored) {}
			}
		}
		ctx.exceptionCaught(ex);
	}
}