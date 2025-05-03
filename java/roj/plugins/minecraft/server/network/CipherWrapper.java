package roj.plugins.minecraft.server.network;

import roj.crypt.RCipherSpi;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * @author Roj234
 * @since 2024/3/19 21:50
 */
public class CipherWrapper implements ChannelHandler {
	private final RCipherSpi encrypt, decrypt;
	private final boolean stream;
	public CipherWrapper(RCipherSpi encrypt, RCipherSpi decrypt, boolean stream) {
		this.encrypt = encrypt;
		this.decrypt = decrypt;
		this.stream = stream;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int outSize = decrypt.engineGetOutputSize(in.readableBytes());
		if (outSize == 0) {
			ctx.channelRead(in);
			return;
		}
		try (DynByteBuf out = ctx.allocate(in.isDirect(), outSize)) {
			if (stream) decrypt.crypt(in, out);
			else decrypt.cryptFinal(in, out);

			ctx.channelRead(out);
		} catch (GeneralSecurityException e) {
			throw new IOException("加解密操作失败了", e);
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int outSize = encrypt.engineGetOutputSize(in.readableBytes());
		if (outSize == 0) {
			ctx.channelWrite(in);
			return;
		}
		try (DynByteBuf out = ctx.allocate(in.isDirect(), outSize)) {
			if (stream) encrypt.crypt(in, out);
			else encrypt.cryptFinal(in, out);

			ctx.channelWrite(out);
		} catch (GeneralSecurityException e) {
			throw new IOException("加解密操作失败了", e);
		}
	}
}