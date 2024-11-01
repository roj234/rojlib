package roj.net.handler;

import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;

import static roj.util.ByteList.EMPTY;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
public class MSSCrypto extends PacketMerger {
	private final MSSEngine engine;

	private static final byte TLS13 = 1;
	private byte flag;

	static final byte P_DATA = 0x40;

	public MSSCrypto() {this.engine = MSSContext.getDefault().clientEngine();}
	public MSSCrypto(MSSEngine engine) {this.engine = engine;}

	public MSSCrypto sslMode() {flag |= TLS13;return this;}
	public MSSEngine getEngine() {return engine;}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		engine.context = ctx;
		handshake(ctx, EMPTY);
		if (engine.isHandshakeDone()) ctx.channelOpened();
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (!ctx.isOutputOpen()) return;

		var in = (DynByteBuf) msg;

		int w = in.readableBytes();
		var isEarlyData = engine.getDecoder() == null;
		int lengthBytes = isEarlyData ? 2 : VarintSplitter.getVarIntLength(w);
		if (lengthBytes > 3) throw new MSSException("要包装的数据过大");

		var encoder = Objects.requireNonNull(engine.getEncoder(), "无法在此时发送数据");
		int outputSize = encoder.engineGetOutputSize(w);

		var out = ctx.allocate(false, 1 + lengthBytes + outputSize);
		try {
			if (isEarlyData) out.put(MSSEngine.P_PREDATA).putShort(outputSize);
			else out.put(P_DATA).putVUInt(outputSize);

			encoder.cryptFinal(in, out);

			ctx.channelWrite(out);
		} catch (GeneralSecurityException e) {
			throw new MSSException(MSSEngine.CIPHER_FAULT, "加密失败", e);
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
		while (in.readableBytes() > 2) {
			int pos = in.rIndex;
			if (in.getU(pos) != P_DATA) {
				int packet = engine.readPacket(in);
				throw new MSSException(MSSEngine.ILLEGAL_PACKET, ""+packet, null);
			}

			in.rIndex = pos+1;
			int len = VarintSplitter.readVUInt(in, 3);
			if (len < 0 || in.readableBytes() < len) {
				in.rIndex = pos;
				break;
			}

			in.wIndex(in.rIndex + len);
			var decoder = engine.getDecoder();

			int size = decoder.engineGetOutputSize(len);
			try(var out = ctx.allocate(in.isDirect(), size)) {
				decoder.cryptFinal(in, out);
				mergedRead(ctx, out);
			} catch (GeneralSecurityException e) {
				throw new MSSException(MSSEngine.CIPHER_FAULT, "解密失败", e);
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
		if (ex instanceof MSSException e && e.shouldNotifyRemote()) {
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