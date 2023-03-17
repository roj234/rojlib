package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * 让ECB模式的块密码支持流式OFB/CFB/CTR和CBC/PCBC/CTS
 *
 * @author Roj233
 * @since 2021/12/27 21:35
 */
public class MyCipher implements CipheR {
	public static final String MC_MODE = "MC_MODE", IV = "IV", MC_DECRYPT = "MC_DECRYPT";
	public static final int MODE_ECB = 0, MODE_CBC = 1, MODE_PCBC = 2, MODE_CTS = 3, MODE_OFB = 4, MODE_CFB = 5, MODE_CTR = 6;

	public final CipheR cip;

	protected final ByteList iv, state;

	protected byte type, mode;

	public MyCipher(CipheR cip) {
		if (!cip.isBaseCipher() || cip.getBlockSize() == 0) throw new IllegalArgumentException("Not a block cipher");
		this.cip = cip;
		this.iv = new ByteList(cip.getBlockSize());
		this.state = new ByteList(cip.getBlockSize());
	}

	public MyCipher(CipheR cip, int i) {
		this(cip);
		type = (byte) i;
	}

	@Override
	public String getAlgorithm() {
		return cip.getAlgorithm();
	}

	@Override
	public int getMaxKeySize() {
		return cip.getMaxKeySize();
	}

	@Override
	public void setKey(byte[] key, int cryptFlags) {
		this.mode = (byte) cryptFlags;
		switch (type) {
			case MODE_OFB:
			case MODE_CFB:
			case MODE_CTR:
				cryptFlags &= ~DECRYPT;
				break;
		}
		cip.setKey(key, cryptFlags);
		reset();
	}

	@Override
	public void setOption(String key, Object value) {
		switch (key) {
			case MC_DECRYPT:
				this.mode = (byte) (value==Boolean.TRUE?1:0);
				break;
			case MC_MODE:
				type = ((Number) value).byteValue();
				break;
			case IV:
				reset();
				System.arraycopy(value, 0, iv.array(), 0, iv.capacity());
				break;
			default:
				cip.setOption(key, value);
				break;
		}
	}

	final void reset() {
		Arrays.fill(iv.array(), (byte) 0);
		iv.rIndex = 0;
		iv.wIndex(iv.capacity());
		state.clear();
	}

	@Override
	public int getBlockSize() {
		switch (type) {
			case MODE_ECB: case MODE_CBC:
			case MODE_PCBC: case MODE_CTS:
				return cip.getBlockSize();
			default: return 0;
		}
	}

	@Override
	public boolean isBaseCipher() {
		return false;
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		if (out.writableBytes() < getCryptSize(in.readableBytes())) throw new ShortBufferException();

		switch (type) {
			case MODE_ECB: // Electronic Cipher Book
			case MODE_CBC: // Cipher Block Chaining
			case MODE_PCBC: // Plain Cipher Block Chaining
				//if (padding != null) padding.pa
				blockCipher(type, in, out);
				break;
			case MODE_CTS:
				CTS(in, out);
				break;
			default:
				if ((iv.capacity() & 3) != 0 || !try4(in, out)) try1(in, out, in.readableBytes());
		}
	}

	private void CTS(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		int blockSize = iv.capacity();

		int len = in.readableBytes();
		if (len < blockSize) throw new ShortBufferException();

		if (len == blockSize) {
			CBC(in, out, 1);
			return;
		}

		CBC(in, out, len / blockSize-1);
		int ext = len%blockSize;
		int pos = out.wIndex();

		byte[] iv = this.iv.array();
		ByteList t0 = state;
		byte[] tmp = t0.array();

		if ((mode & DECRYPT) == 0) {
			out.wIndex(pos+blockSize);

			// Copied CBC method, block N-1
			for (int i = 0; i < blockSize; i++) {
				tmp[i] = (byte) (in.get() ^ iv[i]);
			}

			t0.clear(); t0.wIndex(blockSize);
			cip.cryptInline(t0, blockSize);

			out.put(t0, ext);

			// E^P + X, block N
			for (int i = 0; i < ext; i++) tmp[i] ^= in.get();

			t0.clear(); t0.wIndex(blockSize);
			out.wIndex(pos);
			cip.crypt(t0, out);
			out.wIndex(pos + blockSize + ext);
		} else {
			t0.clear();
			cip.crypt(in, t0);

			out.wIndex(pos + blockSize + ext);
			// block N first (E^P)
			int off = pos + blockSize;
			for (int i = 0; i < ext; i++)
				out.put(off++, (byte) (in.get(in.rIndex + i) ^ tmp[i]));

			// replace X
			t0.clear(); t0.put(in, ext); t0.wIndex(blockSize);

			out.wIndex(pos);
			cip.crypt(t0, out);
			for (int i=0; i<blockSize; i++)
				out.put(pos+i, (byte) (out.get(pos+i) ^ iv[i]));

			out.wIndex(pos + blockSize + ext);
		}
	}

	protected final void blockCipher(byte type, DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		ByteList iv = this.iv;
		ByteList t0 = this.state;
		int blockSize = iv.capacity();

		int cyl = in.readableBytes() / blockSize;

		switch (type) {
			case MODE_ECB:
				while (cyl-- > 0) cip.crypt(in, out);
				break;
			case MODE_CBC:
				CBC(in, out, cyl);
			break;
			case MODE_PCBC: {
				byte[] ivArr = iv.array();
				int inPos = in.rIndex;
				if ((mode & DECRYPT) == 0) {
					int outPos = out.rIndex;
					while (cyl-- > 0) {
						for (int i = 0; i < blockSize; i++) ivArr[i] ^= in.get(inPos++);

						iv.rIndex = 0;
						cip.crypt(iv, out);

						in.read(ivArr);

						for (int i = 0; i < blockSize; i++) ivArr[i] ^= out.get(outPos++);
					}
				} else {
					byte[] tmp = t0.array();
					while (cyl-- > 0) {
						t0.clear();
						cip.crypt(in, t0);

						for (int i = 0; i < blockSize; i++) {
							int b = tmp[i] ^ ivArr[i];
							out.put((byte) b);
							ivArr[i] = (byte) (b ^ in.get(inPos++));
						}
					}
				}
			}
			break;
		}
	}

	private void CBC(DynByteBuf in, DynByteBuf out, int cyl) throws GeneralSecurityException {
		ByteList iv = this.iv;
		ByteList st = this.state;
		int blockSize = iv.capacity();

		byte[] ivArr = iv.array();
		byte[] tmp = st.array();

		if ((mode & DECRYPT) == 0) {
			st.wIndex(blockSize);

			while (cyl-- > 0) {
				for (int i = 0; i < blockSize; i++)
					tmp[i] = (byte) (in.get() ^ ivArr[i]);

				st.rIndex = 0;
				iv.clear();
				cip.crypt(st, iv);

				out.put(iv);
			}
		} else {
			int w = in.wIndex();
			in.wIndex(in.rIndex);

			while (cyl-- > 0) {
				int j = in.wIndex();

				in.wIndex(j+blockSize); st.clear();
				cip.crypt(in, st);

				for (int i = 0; i < blockSize; i++) {
					int b = ivArr[i]^tmp[i];
					ivArr[i] = in.get(i+j);
					out.put((byte) b);
				}
			}
			in.wIndex(w);
		}
	}

	protected final void try1(DynByteBuf in, DynByteBuf out, int cyl) throws GeneralSecurityException {
		ByteList iv = this.iv;
		ByteList t0 = this.state;

		switch (type) {
			case MODE_OFB: { // Output Feedback
				while (cyl-- > 0) {
					if (!iv.isReadable()) {
						iv.rIndex = 0;
						t0.clear();
						cip.crypt(iv, t0);

						System.arraycopy(t0.array(), 0, iv.array(), 0, iv.capacity());

						iv.rIndex = 0;
						iv.wIndex(iv.capacity());
					}
					out.put((byte) (in.get() ^ iv.get()));
				}
			}
			break;
			case MODE_CFB: { // Cipher Feedback
				boolean ENC = (mode & DECRYPT) == 0;
				while (cyl-- > 0) {
					if (!iv.isReadable()) {
						iv.clear();
						cip.crypt(t0, iv);
						t0.clear();
					}
					byte b = in.get();
					if (ENC) t0.put(b);
					b ^= iv.get();
					if (!ENC) t0.put(b);
					out.put(b);
				}
			}
			break;
			case MODE_CTR: {// Counter
				byte[] ctr = iv.array(); iv.wIndex(iv.capacity());
				while (cyl-- > 0) {
					if (!t0.isReadable()) {
						t0.clear();
						iv.rIndex = 0;
						cip.crypt(iv, t0);
						increment(iv.array());
					}
					out.put((byte) (in.get() ^ t0.get()));
				}
			}
			break;
		}
	}

	protected final boolean try4(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		ByteList iv = this.iv;
		ByteList t0 = this.state;

		if (in.readableBytes() < 4) return false;
		align(in, out, type == MODE_CTR ? t0 : iv);
		int cyl = in.readableBytes() >> 2;
		if (cyl == 0) return false;

		switch (type) {
			case MODE_OFB:
				do {
					if (!iv.isReadable()) {
						iv.rIndex = 0;
						t0.clear();
						cip.crypt(iv, t0);

						System.arraycopy(t0.array(), 0, iv.array(), 0, iv.capacity());

						iv.rIndex = 0;
						iv.wIndex(iv.capacity());
					}
					out.putInt(in.readInt() ^ iv.readInt());
				} while (--cyl > 0);
				break;
			case MODE_CFB: {
				boolean ENC = (mode & DECRYPT) == 0;
				if (ENC) {
					do {
						if (!iv.isReadable()) {
							iv.clear();
							cip.crypt(t0, iv);
							t0.clear();
						}
						int b = in.readInt();
						t0.putInt(b);
						out.putInt(b ^ iv.readInt());
					} while (--cyl > 0);
				} else {
					do {
						if (!iv.isReadable()) {
							iv.clear();
							cip.crypt(t0, iv);
							t0.clear();
						}
						int b = in.readInt() ^ iv.readInt();
						t0.putInt(b);
						out.putInt(b);
					} while (--cyl > 0);
				}
			}
			break;
			case MODE_CTR: {
				byte[] ctr = iv.array(); iv.wIndex(iv.capacity());
				do {
					if (!t0.isReadable()) {
						t0.clear();
						iv.rIndex = 0;
						cip.crypt(iv, t0);
						increment(iv.array());
					}
					out.putInt(in.readInt() ^ t0.readInt());
				} while (--cyl > 0);
			}
			break;
		}

		return !in.isReadable();
	}

	private void align(DynByteBuf in, DynByteBuf out, DynByteBuf buf) throws GeneralSecurityException {
		if ((buf.rIndex & 3) != 0) try1(in, out, 4 - (buf.rIndex & 3));
	}

	/**
	 * Increment the counter value.
	 */
	protected void increment(byte[] b) {
		int n = b.length - 1;
		while ((n >= 0) && (++b[n] == 0)) {
			n--;
		}
	}

	@Override
	public String toString() {
		return "MyCipher{" + "cip=" + cip + ", type=" + type + ", mode=" + mode + '}';
	}
}
