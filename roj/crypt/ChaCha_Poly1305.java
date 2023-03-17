package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * ChaCha_Poly1305: <a href="https://www.rfc-editor.org/info/rfc8439">RFC8439</a>
 *
 * @author solo6975
 * @since 2022/2/14 13:40
 */
public final class ChaCha_Poly1305 extends RCipherSpi {
	final ChaCha c;
	final Poly1305 p = new Poly1305();
	final ByteList tmp = ByteList.allocate(32,32);

	private long lenAAD, processed;

	private boolean decrypt;
	private byte state;

	private ChaCha_Poly1305(ChaCha c) { this.c = c; }
	public static RCipherSpi ChaCha1305() { return new ChaCha_Poly1305(new ChaCha()); }
	public static RCipherSpi XChaCha1305() { return new ChaCha_Poly1305(new XChaCha()); }

	public final void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		c.init(mode, key, par, random);
		this.decrypt = mode == Cipher.DECRYPT_MODE;
	}

	public int engineGetOutputSize(int data) { return decrypt ? data - 16 : data + 16; }

	@Override
	public final void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		if (state != 2) {
			if (state == 0) cryptBegin();
			else finishAAD();
			state = 2;
		}

		if (decrypt) {
			in.wIndex(in.wIndex() - 16);
			if (in.isReadable()) {
				processed += in.readableBytes();

				int pos = in.rIndex;
				p.update(in);
				in.rIndex = pos;

				c.crypt(in, out);
			}
			in.wIndex(in.wIndex() + 16);
		} else {
			processed += in.readableBytes();

			int pos = out.wIndex();

			c.crypt(in, out);
			p.update(out.slice(pos, out.wIndex()-pos));
		}
	}
	public void cryptFinal1(DynByteBuf in, DynByteBuf out) throws ShortBufferException, BadPaddingException {
		if (decrypt) {
			if (in.readableBytes() < 16) throw new ShortBufferException();

			finalBlock();
			tmp.clear();
			p.digest(tmp);

			int v = 0;
			for (int i = 3; i >= 0; i--) v |= tmp.readInt() ^ in.readInt();
			if (v != 0) throw new AEADBadTagException();
		} else {
			if (out.writableBytes() < 16) throw new ShortBufferException();

			finalBlock();
			p.digest(out);
		}
	}

	public final void insertAAD(DynByteBuf aad) {
		if (state == 0) {
			cryptBegin();
			state = 1;
		} else if (state > 1) throw new IllegalStateException("AAD 必须在加密开始前提供");

		lenAAD += aad.readableBytes();
		p.update(aad);
	}
	private void finishAAD() {
		int len = (int) (lenAAD & 15);
		while ((len++ & 15) != 0) p.update((byte) 0);
	}

	private void cryptBegin() {
		ChaCha c = this.c;

		c.reset();
		c.KeyStream();

		tmp.clear();
		for (int i = 0; i < 8; i++) tmp.putInt(c.tmp[i]);

		p.setSignKey(tmp.list);

		lenAAD = processed = 0;
	}
	private void finalBlock() {
		int len = (int) (processed & 15);
		while ((len++ & 15) != 0) p.update((byte) 0);

		ByteList tb = tmp; tb.clear();
		tb.putLongLE(lenAAD).putLongLE(processed);
		p.update(tb.list, 0, 16);
	}
}
