package roj.net.proto_obf;

import roj.crypt.ChaCha_Poly1305;
import roj.crypt.MT19937;
import roj.crypt.RCipherSpi;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.MyChannel;
import roj.net.ch.handler.MSSCipher;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSEngineClient;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.crypto.Cipher;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * @author Roj234
 * @since 2023/9/15 0015 22:01
 */
public class ProtoObf extends LengthFake {
	public static void install(MyChannel ch) { install(ch, new MSSEngineClient()); }
	public static void install(MyChannel ch, MSSEngine engine) {
		MT19937 rnd = new MT19937();
		ch.addLast("_proto_len_", new LengthFake(rnd))
		  .addLast("_proto_obf_", new Obfuscate())
		  .addLast("_proto_tls_", new MSSCipher(engine))
		  .addLast("_proto_enc_", new ProtoObf(rnd));
	}

	private final RCipherSpi
		c_in = ChaCha_Poly1305.XChaCha1305(),
		c_out = ChaCha_Poly1305.XChaCha1305();

	private final ByteList c_len = new ByteList(5);
	private boolean inited, fakeEnable, fakeSending;
	private int fakeTimer;

	public ProtoObf(MT19937 rnd) { super(rnd); }

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		MSSEngine engine = ((MSSCipher) ctx.prev().handler()).getEngine();

		try {
			if (engine.isClientMode()) {
				c_in.init(Cipher.DECRYPT_MODE, engine.deriveKey("hello", 32), null, engine.getPRNG("_proto_hello"));
				c_out.init(Cipher.ENCRYPT_MODE, engine.deriveKey("byebye", 32), null, engine.getPRNG("_proto_byebye"));
			} else {
				c_in.init(Cipher.DECRYPT_MODE, engine.deriveKey("byebye", 32), null, engine.getPRNG("_proto_byebye"));
				c_out.init(Cipher.ENCRYPT_MODE, engine.deriveKey("hello", 32), null, engine.getPRNG("_proto_hello"));
			}
		} catch (GeneralSecurityException e) {
			Helpers.athrow(e);
		}

		ctx.prev().dispose(); // _proto_tls_
		ctx.prev().dispose(); // _proto_obf_
		ctx.prev().dispose(); // _proto_len_
		ctx.channelOpened();
		inited = fakeEnable = true;
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws Exception {
		if (!fakeEnable) return;

		super.channelTick(ctx);
		if (buffers.isEmpty()) {
			// 虚假回包
			if (--fakeTimer == 0) {
				fakeSending = true;

				try {
					int len = rnd.nextInt(2000);
					ByteList b = IOUtil.getSharedByteBuf(); b.wIndex(len);
					rnd.nextBytes(b.list, 0, len);
					super.channelWrite(ctx, b);
				} finally {
					fakeSending = false;
				}
			}

			if (fakeTimer < 0) fakeTimer = rnd.nextInt(1000);
		} else {
			fakeTimer = 0;
		}
	}

	@Override
	public void channelFlushing(ChannelCtx ctx) {
		fakeEnable = false;
		super.channelFlushing(ctx);
	}
	@Override
	public void channelFlushed(ChannelCtx ctx) { fakeEnable = inited; }

	@Override
	void readPacket(ChannelCtx ctx, DynByteBuf in) throws IOException {
		DynByteBuf out = ctx.allocate(true, 5);
		int pos = in.wIndex(); in.wIndex(in.rIndex+21);
		try {
			c_in.crypt(in, out);
			int state = out.readUnsignedByte();
			int len = out.readInt();
			in.wIndex(in.rIndex+len+16);

			out.clear(); if (len > 5) out = ctx.alloc().expand(out, len-5);
			c_in.cryptFinal(in, out);

			if ((state & 1) == 0) super.mergedRead(ctx, out);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			ctx.close();
		} finally {
			ctx.reserve(out);
		}
	}

	@Override
	DynByteBuf writePacket(ChannelCtx ctx, DynByteBuf out, DynByteBuf in, int len) throws IOException {
		if (out.writableBytes() < len+21) out = ctx.alloc().expand(out, len+21-out.writableBytes());

		try {
			c_len.clear();

			// 偶数为真实包
			int flag = rnd.nextInt(256) & ~1;
			if (fakeSending) flag++;

			c_out.crypt(c_len.put(flag).putInt(len), out);
			c_out.cryptFinal(in.slice(len), out);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			ctx.close();
		}
		return out;
	}

	// 随机长度
	protected int _length(DynByteBuf buf) {
		float p = rnd.nextFloat();
		if (p < 0.1f || buf.readableBytes() < p*100) return buf.readableBytes();
		if (p < 0.7f) return rnd.nextInt(buf.readableBytes()+1);
		if (p < 0.9f) return rnd.nextInt(buf.readableBytes()+1)+rnd.nextInt(buf.readableBytes()+1);
		return rnd.nextInt(4096);
	}
}
