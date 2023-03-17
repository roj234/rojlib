package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.AEADBadTagException;
import javax.crypto.ShortBufferException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * ChaCha_Poly1305: <br>
 * <a href="https://www.rfc-editor.org/info/rfc8439">RFC8439</a>
 *
 * @author solo6975
 * @since 2022/2/14 13:40
 */
public class ChaCha_Poly1305 implements CipheR {
	public static final String NONCE = "IV", AAD = "AAD", PRNG = "PRNG";
	static final int INITED = 2;

	final ChaCha c;
	final Poly1305 p;
	final ByteList tb;

	private byte[] aad;
	private byte flag;
	private long processed;

	Random prng;

	public ChaCha_Poly1305() {
		this(new ChaCha());
	}

	public ChaCha_Poly1305(ChaCha chacha) {
		this.c = chacha;
		this.p = new Poly1305();
		this.tb = new ByteList(32);
	}

	public final void setPRNG(Random rng) {
		this.prng = rng;
	}

	public final Random getPRNG() {
		return prng;
	}

	@Override
	public String getAlgorithm() {
		return "ChaCha_Poly1305";
	}

	@Override
	public int getMaxKeySize() {
		return 32;
	}

	@Override
	public final void setKey(byte[] key, int flags) {
		c.setKey(key, flags);
		this.flag = (byte) flags;
	}

	public final void setAAD(byte[] aad) {
		this.aad = aad;
	}

	// Note that it is not acceptable to use a truncation of a counter encrypted with a
	// 128-bit or 256-bit cipher, because such a truncation may repeat after a short time.
	public final void setNonce(byte[] nonce) {
		c.setNonce(nonce);
	}

	@Override
	public final void setOption(String key, Object value) {
		switch (key) {
			case NONCE:
				c.setNonce((byte[]) value);
				break;
			case AAD:
				aad = (byte[]) value;
				break;
			case PRNG:
				prng = (Random) value;
				break;
		}
	}

	@Override
	public final int getBlockSize() {
		return 0;
	}

	@Override
	public int getCryptSize(int data) {
		return (flag & DECRYPT) != 0 ? data - 16 : data + 16;
	}

	void generateNonce(int[] key) {
		key[13] = prng.nextInt();
		key[14] = prng.nextInt();
		key[15] = prng.nextInt();
	}

	@Override
	public final void crypt(DynByteBuf in, DynByteBuf out) throws AEADBadTagException, ShortBufferException {
		if ((flag & CipheR.DECRYPT) == 0) {
			if (out.writableBytes() < in.readableBytes() + 16) throw new ShortBufferException();
			cryptBegin();
			encrypt(in, out);
			out.put(getHash().list, 0, 16);
		} else {
			if (out.writableBytes() < in.readableBytes() - 16) throw new ShortBufferException();
			if (!in.isReadable()) return;
			cryptBegin();
			in.wIndex(in.wIndex()-16);
			decrypt(in, out);
			in.wIndex(in.wIndex()+16);
			decryptFinal(in);
		}
	}

	public final void cryptBegin() {
		ChaCha c = this.c;
		int[] key = c.key;

		if (prng != null) generateNonce(key);

		key[12] = 0;
		c.reset();
		c.KeyStream();

		byte[] l = tb.list;
		Conv.i2b(c.tmp, 0, 8, l, 0);

		Poly1305 p = this.p;
		p.setSignKey(l);

		byte[] aad = this.aad;
		if (aad != null) {
			int len = aad.length;
			p.update(aad);
			while ((len++ & 15) != 0) p.update((byte) 0);
		}

		processed = 0;
		flag |= INITED;
	}
	public final void encrypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		int len = in.readableBytes();
		int pos = out.wIndex();
		c.crypt(in, out);

		if (out.hasArray()) p.update(out.array(), out.arrayOffset()+pos, out.wIndex()-pos);
		else {
			ByteBuffer buf = out.nioBuffer();
			buf.limit(out.wIndex()).position(pos);
			p.update(buf);
		}

		processed += len;
	}
	public final void decrypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (in.hasArray()) p.update(in.array(), in.arrayOffset()+in.rIndex, in.readableBytes());
		else {
			ByteBuffer bb = in.nioBuffer();
			bb.limit(bb.limit() - 16);
			p.update(bb);
		}

		processed += in.readableBytes();

		c.crypt(in, out);
	}
	public final void decryptFinal(DynByteBuf in) throws AEADBadTagException {
		ByteList h = getHash();
		int ok = 0;
		//  If the timing of the tag comparison operation reveals how long
		//  a prefix of the calculated and received tags is identical.
		for (int i = 3; i >= 0; i--) {
			ok |= h.readInt() ^ in.readInt();
		}
		if (ok != 0) throw new AEADBadTagException();
	}
	public final ByteList getHash() {
		if ((flag & INITED) == 0) return tb;

		int len = (int) (processed & 15);
		while ((len++ & 15) != 0) p.update((byte) 0);

		ByteList tb = this.tb;
		tb.clear();
		tb.putLongLE(aad == null ? 0 : aad.length).putLongLE(processed);
		p.update(tb.list, 0, 16);
		tb.clear();

		p._digestFinal(p.bList, tb);

		flag &= ~INITED;

		return tb;
	}
}
