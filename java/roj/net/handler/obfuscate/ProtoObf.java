package roj.net.handler.obfuscate;

import roj.crypt.ChaCha_Poly1305;
import roj.crypt.MT19937;
import roj.crypt.RCipherSpi;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.net.MyChannel;
import roj.net.handler.MSSCipher;
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

	private static final int MASK0 = 31;
	private final int[] packetSize = new int[MASK0+1];
	private int cPtr;

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
		super.channelTick(ctx);

		if (fakeEnable && buffers.isEmpty()) {
			// 虚假回包
			if (--fakeTimer == 0) {
				int len = _length(-1);

				fakeSending = true;
				try {
					ByteList b = IOUtil.getSharedByteBuf(); b.wIndex(len);
					nextBytes(rnd, b.list, 0, len);
					super.channelWrite(ctx, b);
				} finally {
					fakeSending = false;
				}
			}

			if (fakeTimer == -1) {
				float p = rnd.nextFloat();
				if (p < 0.11f) fakeTimer = _delay(-1);
				if (p < 0.33f) fakeTimer = 1000+rnd.nextInt(60000);
				if (p < 0.66f) fakeTimer = rnd.nextInt(3000);
				else fakeTimer = -2;
			}
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
		if (fakeTimer == -2) fakeTimer = -1;

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
			BufferPool.reserve(out);
		}
	}

	@Override
	DynByteBuf writePacket(ChannelCtx ctx, DynByteBuf out, DynByteBuf in, int len) throws IOException {
		if (out.writableBytes() < len+21) out = ctx.alloc().expand(out, len+21-out.writableBytes());

		// 偶数为真实包
		int flag = rnd.nextInt(256) & ~1;

		if (fakeSending) flag++;
		else if (fakeTimer > 0) fakeTimer += rnd.nextInt(3000);

		try {
			c_len.clear();
			c_out.crypt(c_len.put(flag).putInt(len), out);
			c_out.cryptFinal(in.slice(len), out);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			ctx.close();
		}
		return out;
	}

	// 随机长度
	int _length(int buf) {
		if (buf > 0) {
			if (fakeTimer == -2) fakeTimer = -1;
			packetSize[cPtr++ & MASK0] = buf;
		} else {
			buf = packetSize[cPtr & MASK0];
			if (buf == 0) buf = 1+rnd.nextInt(4095);
		}

		int sample = packetSize[rnd.nextInt((cPtr & ~MASK0) == 0 ? cPtr+1 : MASK0)]+1;
		sample = rnd.nextInt(sample)+rnd.nextInt(sample);

		float p = rnd.nextFloat();
		if (p < 0.1f) return buf;
		if (p > 0.3f && p < 0.7f) return (int) (rnd.nextInt(buf)*p+sample*(1-p));

		buf++;

		if (p < 0.9f) return rnd.nextInt(buf);
		return rnd.nextInt(buf)+rnd.nextInt(buf);
	}
}