package roj.net.handler;

import roj.crypt.RCipherSpi;
import roj.net.ChannelCtx;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/4/26 0026 23:19
 */
public final class StreamCipher extends PacketMerger {
	private final RCipherSpi encrypt, decrypt;

	public StreamCipher(RCipherSpi encrypt, RCipherSpi decrypt) {
		this.encrypt = encrypt;
		this.decrypt = decrypt;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;
		decrypt.cryptInline(buf, decrypt.engineGetOutputSize(buf.readableBytes()));
		mergedRead(ctx, buf);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;
		try (var ob = ctx.allocate(true, encrypt.engineGetOutputSize(buf.readableBytes()))) {
			encrypt.cryptFinal(buf, ob);
			ctx.channelWrite(ob);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}
}