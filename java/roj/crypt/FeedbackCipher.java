package roj.crypt;

import org.intellij.lang.annotations.MagicConstant;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Locale;

/**
 * 让ECB模式的块密码支持流式OFB/CFB/CTR和CBC/PCBC/CTS
 *
 * @author Roj233
 * @since 2021/12/27 21:35
 */
public class FeedbackCipher extends RCipherSpi {
	public Cipher toJavaCipher(boolean feedbackModeAndPadding) {
		return _toJavaCipher(this);
	}

	public final void engineSetMode(String s) throws NoSuchAlgorithmException {
		switch (s.toUpperCase(Locale.ROOT)) {
			case "ECB": type = MODE_ECB; tmp = new ByteList(); return;
			case "CBC": type = MODE_CBC; break;
			case "PCBC":type = MODE_PCBC; break;
			case "CTS": type = MODE_CTS; break;
			case "OFB8": type = MODE_OFB; break;
			case "CFB8": type = MODE_CFB; break;
			case "CTR": type = MODE_CTR; break;
			default: throw new NoSuchAlgorithmException(s);
		}

		tmp = type == MODE_OFB ? new ByteList(vec.array()) : new ByteList(cip.engineGetBlockSize());
	}

	@Override
	public void engineSetPadding(String s) throws NoSuchPaddingException {
		switch (s.toUpperCase(Locale.ROOT)) {
			case "NOPADDING": padding = 0; break;
			case "PKCS5PADDING":
			case "PKCS7PADDING": padding = 1; break;
			case "ISO10126PADDING": padding = 2; break;
			default: throw new NoSuchPaddingException(s);
		}
	}

	protected String getMode() {
		switch (type) {
			default:
			case MODE_ECB: return "ECB";
			case MODE_CBC: return "CBC";
			case MODE_PCBC: return "PCBC";
			case MODE_CTS: return "CTS";
			case MODE_OFB: return "OFB8";
			case MODE_CFB: return "CFB8";
			case MODE_CTR: return "CTR";
		}
	}

	public static final int MODE_ECB = 0, MODE_CBC = 1, MODE_PCBC = 2, MODE_CTS = 3, MODE_OFB = 4, MODE_CFB = 5, MODE_CTR = 6;

	protected final RCipherSpi cip;
	protected ByteList vec, tmp;

	private byte type, padding;
	protected boolean decrypt;

	public FeedbackCipher(RCipherSpi cip, @MagicConstant(intValues = {MODE_ECB,MODE_CBC,MODE_PCBC,MODE_CTS,MODE_OFB,MODE_CFB,MODE_CTR}) int mode) {
		if (!cip.isBareBlockCipher() || cip.engineGetBlockSize() == 0) throw new IllegalArgumentException("Not a block cipher");

		this.cip = cip;
		this.vec = new ByteList(cip.engineGetBlockSize());

		type = (byte) mode;
		try {
			engineSetMode(getMode());
		} catch (NoSuchAlgorithmException ignored) {}
	}
	public FeedbackCipher(RCipherSpi cip, String mode, String padding) throws NoSuchAlgorithmException, NoSuchPaddingException {
		if (!cip.isBareBlockCipher() || cip.engineGetBlockSize() == 0) throw new IllegalArgumentException("Not a block cipher");

		this.cip = cip;

		engineSetMode(mode);
		engineSetPadding(padding);
	}

	@Override
	public String getAlgorithm() { return cip.getAlgorithm()+"/"+ getMode()+"/"+"NoPadding"; }

	@Override
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		decrypt = mode == Cipher.DECRYPT_MODE;
		switch (type) {
			case MODE_OFB: case MODE_CFB: case MODE_CTR:
				mode = Cipher.ENCRYPT_MODE;
			break;
		}
		if (key != null) cip.init(mode, key);

		vec.clear();
		tmp.clear();

		byte[] ivArr;
		if (par instanceof IvParameterSpec) {
			ivArr = ((IvParameterSpec) par).getIV();
		} else if (par instanceof IvParameterSpecNC) {
			ivArr = ((IvParameterSpecNC) par).getIV();
		} else {
			if (type == MODE_ECB) return;
			throw new InvalidAlgorithmParameterException("必须提供IV");
		}
		if (type == MODE_ECB) throw new InvalidAlgorithmParameterException("ECB不能使用IV");

		if (ivArr.length != vec.capacity()) throw new InvalidAlgorithmParameterException("IV的长度必须是一个块");
		vec.put(ivArr);
	}

	final void reset() {
		Arrays.fill(vec.array(), (byte) 0);
		vec.clear();
		tmp.clear();
	}

	@Override
	public int engineGetBlockSize() {
		switch (type) {
			case MODE_ECB: case MODE_CBC:
			case MODE_PCBC: case MODE_CTS:
				return cip.engineGetBlockSize();
			default: return 0;
		}
	}

	@Override
	public int engineGetOutputSize(int in) {
		if (padding != 0) {
			in |= vec.capacity()-1;
			in++;
		}
		return in;
	}

	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < engineGetOutputSize(in.readableBytes())) throw new ShortBufferException();

		switch (type) {
			case MODE_ECB: cip.crypt(in, out); break;
			case MODE_CBC: CipherBlockChain(in, out, in.readableBytes() / vec.capacity()); break;
			case MODE_PCBC: PlainCipherBlockChain(in, out);break;
			case MODE_CTS: CipherBlockChain(in, out, in.readableBytes() / vec.capacity() - 2); break;
			default: try4(in, out); try1(in, out, in.readableBytes()); break;
		}
	}
	protected void cryptFinal1(DynByteBuf in, DynByteBuf out) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		switch (type) {
			case MODE_ECB: case MODE_CBC: case MODE_PCBC:
				if (padding != 0) {
					if (decrypt) FC_Unpad(vec.capacity(), out, padding == 2);
					else {
						FC_Pad(vec.capacity(), in, padding == 2);
						crypt(in, out);
					}
				}
				break;
			case MODE_CTS: CiphertextStealing(in, out);break;
			default: try1(in, out, in. readableBytes()); break;
		}
		if (in.isReadable()) throw new IllegalBlockSizeException("Input length not multiple of "+ vec.capacity()+" bytes");
		reset();
	}

	private void CipherBlockChain(DynByteBuf in, DynByteBuf out, int cyl) {
		ByteList V = vec;
		byte[] vArr = V.array();
		ByteList T = tmp;
		byte[] tArr = T.array();

		int blockSize = V.capacity();

		if (decrypt) {
			int w = in.wIndex();
			in.wIndex(in.rIndex);

			while (cyl-- > 0) {
				int j = in.wIndex();

				in.wIndex(j+blockSize); T.clear();
				cip.cryptOneBlock(in, T);

				for (int i = 0; i < blockSize; i++) {
					int b = vArr[i]^tArr[i];
					vArr[i] = in.get(i+j);
					out.put(b);
				}
			}
			in.wIndex(w);
		} else {
			T.setArray(vArr);
			while (cyl-- > 0) {
				for (int i = 0; i < blockSize; i++) vArr[i] ^= in.readByte();

				T.clear();
				cip.cryptOneBlock(V, T);
				V.rIndex = 0;

				out.put(V);
			}
			T.setArray(tArr);
		}
	}
	private void PlainCipherBlockChain(DynByteBuf in, DynByteBuf out) {
		ByteList V = vec;
		byte[] vArr = V.array();

		ByteList T = tmp;
		byte[] tArr = T.array();

		int blockSize = V.capacity();

		int cyl = in.readableBytes() / blockSize;

		int inPos = in.rIndex;
		if (decrypt) {
			while (cyl-- > 0) {
				T.clear();
				cip.cryptOneBlock(in, T);

				for (int i = 0; i < blockSize; i++) {
					int b = tArr[i] ^ vArr[i];
					out.put((byte) b);
					vArr[i] = (byte) (b ^ in.get(inPos++));
				}
			}
		} else {
			while (cyl-- > 0) {
				for (int i = 0; i < blockSize; i++) vArr[i] ^= in.get(inPos++);

				V.rIndex = 0; T.clear();
				cip.cryptOneBlock(V, T);

				in.readFully(vArr);

				for (int i = 0; i < blockSize; i++) vArr[i] ^= T.get(i);

				out.put(T);
			}
		}
	}
	private void CiphertextStealing(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		int blockSize = vec.capacity();

		int len = in.readableBytes();
		if (len < blockSize) throw new ShortBufferException();

		// exactly one block
		if (len == blockSize) {
			CipherBlockChain(in, out, 1);
			return;
		}

		// swap last two block
		if (len == 2*blockSize) {
			if (decrypt) {
				in.rIndex += blockSize;
				CipherBlockChain(in, out, 1);
				in.rIndex -= blockSize * 2;
				CipherBlockChain(in, out, 1);
				in.rIndex += blockSize;
			} else {
				out.wIndex(out.wIndex() + blockSize);
				CipherBlockChain(in, out, 1);
				out.wIndex(out.wIndex() - blockSize * 2);
				CipherBlockChain(in, out, 1);
				out.wIndex(out.wIndex() + blockSize);
			}
			return;
		}

		CipherBlockChain(in, out, 1);

		int ext = len%blockSize;
		int pos = out.wIndex();

		byte[] vArr = vec.array();
		ByteList T = tmp; T.clear();
		byte[] tArr = T.array();

		if (!decrypt) {
			// Copied CBC method, block N-1
			for (int i = 0; i < blockSize; i++) vArr[i] ^= in.readByte();

			cip.cryptOneBlock(vec, T);

			out.wIndex(pos+blockSize);
			out.put(T, ext);
			out.wIndex(pos);

			// E^P + X, block N
			for (int i = 0; i < ext; i++) tArr[i] ^= in.readByte();

			cip.cryptOneBlock(T, out);
			out.wIndex(pos+blockSize+ext);
		} else {
			cip.cryptOneBlock(in, T);
			out.wIndex(pos+blockSize+ext);

			// block N first (E^P)
			int off = pos+blockSize;
			for (int i = 0; i < ext; i++)
				out.put(off++, (in.get(in.rIndex+i) ^ tArr[i]));

			// replace X
			T.clear(); T.put(in, ext); T.wIndex(blockSize);
			in.rIndex += ext;

			out.wIndex(pos);
			cip.cryptOneBlock(T, out);
			for (int i=0; i<blockSize; i++)
				out.put(pos+i, (out.get(pos+i) ^ vArr[i]));

			out.wIndex(pos+blockSize+ext);
		}
	}

	protected final void try1(DynByteBuf in, DynByteBuf out, int cyl) {
		ByteList VO = vec, VI = tmp;

		switch (type) {
			case MODE_OFB: // Output Feedback
				while (cyl-- > 0) {
					if (!VI.isReadable()) {
						VI.clear();
						cip.cryptOneBlock(VO, VI);
						VO.rIndex = 0;
					}

					out.put(in.readByte() ^ VI.readByte());
				}
			break;
			case MODE_CFB: // Cipher Feedback
				while (cyl-- > 0) {
					VI.clear();
					cip.cryptOneBlock(VO, VI);
					VO.rIndex = 1; VO.compact();

					int b = in.readByte(), v = VI.list[0];
					VO.put(decrypt ? b : b^v);
					out.put(b^v);
				}
			break;
			case MODE_CTR: // Counter
				while (cyl-- > 0) {
					if (!VI.isReadable()) {
						VI.clear();
						cip.cryptOneBlock(VO, VI);
						VO.rIndex = 0;
						increment(VO.array());
					}

					out.put(in.readByte() ^ VI.readByte());
				}
			break;
		}
	}
	protected final void try4(DynByteBuf in, DynByteBuf out) {
		ByteList VO = vec, VI = tmp;

		if (in.readableBytes() < 4) return;
		if ((VI.rIndex & 3) != 0) try1(in, out, 4 - (VI.rIndex & 3));
		int ints = in.readableBytes() >> 2;

		switch (type) {
			case MODE_OFB:
				while (ints-- > 0) {
					if (!VI.isReadable()) {
						VI.clear();
						cip.cryptOneBlock(VO, VI);
						VO.rIndex = 0;
					}

					out.putInt(in.readInt() ^ VI.readInt());
				}
				break;
			case MODE_CTR:
				while (ints-- > 0) {
					if (!VI.isReadable()) {
						VI.clear();
						cip.cryptOneBlock(VO, VI);
						VO.rIndex = 0;
						increment(VO.array());
					}

					out.putInt(in.readInt() ^ VI.readInt());
				}
			break;
		}
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
		return "MyCipher{" + "cip=" + cip + ", type=" + type + '}';
	}

	public static void FC_Pad(int block, DynByteBuf buf, boolean iso) throws ShortBufferException {
		int srcLen = buf.readableBytes();
		int padLen = block - srcLen % block;

		if (!buf.ensureWritable(padLen)) throw new ShortBufferException("填充需要"+padLen+"字节的额外空间");

		byte num = (byte) padLen;

		if (iso) {
			buf.put(SecureRandom.getSeed(padLen-1)).put(num);
		} else {
			Object ref = buf.array();
			long addr = buf._unsafeAddr() + buf.wIndex();
			while (padLen-- > 0) ReflectionUtils.u.putByte(ref, addr++, num);

			buf.wIndex(buf.wIndex() + (num&0xFF));
		}
	}

	public static void FC_Unpad(int block, DynByteBuf buf, boolean iso) throws BadPaddingException {
		int srcLen = buf.readableBytes();
		int padLen = buf.getU(buf.wIndex()-1);

		if (padLen <= 0 || padLen > block || buf.readableBytes() < padLen) throw new BadPaddingException();

		if (!iso) {
			int num = padLen;

			Object ref = buf.array();
			long addr = buf._unsafeAddr() + buf.wIndex() - 2;
			while (num-- > 0) {
				if ((ReflectionUtils.u.getByte(ref, addr--)&0xFF) != padLen) {
					throw new BadPaddingException();
				}
			}
		}

		buf.wIndex(buf.wIndex()-padLen);
	}
}