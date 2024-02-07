package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.crypto.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

/**
 * @author Roj234
 * @since 2023/4/29 0029 0:08
 */
public abstract class RCipherSpi extends CipherSpi {
	public static final int ENCRYPT_MODE = Cipher.ENCRYPT_MODE;
	public static final int DECRYPT_MODE = Cipher.DECRYPT_MODE;

	public String getAlgorithm() { return getClass().getSimpleName(); }

	public Cipher toJavaCipher(boolean feedbackModeAndPadding) {
		return _toJavaCipher(isBareBlockCipher() ? new FeedbackCipher(this, FeedbackCipher.MODE_ECB) : this);
	}
	static Cipher _toJavaCipher(RCipherSpi spi) { return new Cipher(spi, ILProvider.INSTANCE, spi.getAlgorithm()) {}; }

	@Override
	protected void engineSetMode(String s) throws NoSuchAlgorithmException {
		if (!isBareBlockCipher()) throw new IllegalArgumentException(getClass().getName()+" have no modes");
		if (!s.equalsIgnoreCase("ECB")) throw new NoSuchAlgorithmException("Use FeedbackCipher");
	}

	@Override
	protected void engineSetPadding(String s) throws NoSuchPaddingException {
		if (!isBareBlockCipher()) throw new IllegalArgumentException(getClass().getName()+" have no padding modes");
		if (!s.equalsIgnoreCase("NoPadding")) throw new NoSuchPaddingException("Use FeedbackCipher");
	}

	protected boolean isBareBlockCipher() { return false; }
	public int engineGetBlockSize() { return 0; }
	public int engineGetOutputSize(int in) { return in; }
	protected byte[] engineGetIV() { return null; }
	protected AlgorithmParameters engineGetParameters() { return null; }

	protected final void engineInit(int mode, Key key, SecureRandom random) throws InvalidKeyException {
		try {
			engineInit(mode, key, (AlgorithmParameterSpec) null, random);
		} catch (InvalidAlgorithmParameterException e) {
			throw new ProviderException("Unexpected error", e);
		}
	}
	protected final void engineInit(int mode, Key key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		if (key == null) throw new InvalidKeyException("No key given");
		else if (!"RAW".equalsIgnoreCase(key.getFormat())) throw new InvalidKeyException("Wrong format: RAW bytes needed");

		byte[] key1 = key.getEncoded();
		if (key1 == null) throw new InvalidKeyException("RAW key bytes missing");

		init(mode, key1, par, random);
	}
	protected final void engineInit(int mode, Key key, AlgorithmParameters par, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		engineInit(mode, key, par == null ? null : engineFindParameter(par), random);
	}
	protected AlgorithmParameterSpec engineFindParameter(AlgorithmParameters par) throws InvalidAlgorithmParameterException {
		throw new InvalidAlgorithmParameterException();
	}

	protected final void engineUpdateAAD(ByteBuffer aad) {
		DynByteBuf buf = DynByteBuf.nioRead(aad);
		if (buf != null) insertAAD(buf);
		else {
			aad = aad.slice();
			byte[] b = new byte[aad.remaining()];
			aad.get(b);
			insertAAD(new ByteList(b));
		}
	}
	protected final void engineUpdateAAD(byte[] bytes, int off, int len) { insertAAD(roj.io.IOUtil.SharedCoder.get().wrap(bytes, off, len)); }

	// region unify
	protected final byte[] engineUpdate(byte[] b, int off, int len) { return updateIA(b,off,len,false); }
	protected final int engineUpdate(byte[] b, int off, int len, byte[] out, int off1) throws ShortBufferException {
		return updateIAOA(b,off,len,out,off1,false);
	}
	protected final int engineUpdate(ByteBuffer in, ByteBuffer out) throws ShortBufferException {
		int v = updateNoCopy(in, out, false);
		return v < 0 ? super.engineUpdate(in, out) : v;
	}
	protected final byte[] engineDoFinal(byte[] b, int off, int len) throws IllegalBlockSizeException, BadPaddingException { return updateIA(b,off,len,true); }
	protected final int engineDoFinal(byte[] b, int off, int len, byte[] out, int off1) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		return updateIAOA(b,off,len,out,off1,true);
	}
	protected final int engineDoFinal(ByteBuffer in, ByteBuffer out) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		int v = updateNoCopy(in, out, true);
		return v < 0 ? super.engineDoFinal(in, out) : v;
	}

	private byte[] updateIA(byte[] b, int off, int len, boolean doFinal) {
		IOUtil uc = IOUtil.SharedCoder.get(); uc.byteBuf.clear();
		bufferedUpdate(doFinal, uc.wrap(b, off, len), uc.byteBuf);
		return uc.byteBuf.toByteArray();
	}
	private int updateIAOA(byte[] b, int off, int len, byte[] out, int off1, boolean doFinal) {
		IOUtil uc = IOUtil.SharedCoder.get();
		ByteList ob = uc.shellB.set(out, off1, out.length - off1);
		bufferedUpdate(doFinal, uc.wrap(b, off, len), ob);
		return ob.wIndex();
	}
	private int updateNoCopy(ByteBuffer in, ByteBuffer out, boolean doFinal) {
		if (!(in.hasArray() && out.hasArray())) {
			DynByteBuf ib = DynByteBuf.nioRead(in);
			DynByteBuf ob = DynByteBuf.nioWrite(out);
			if (ib != null && ob != null) {
				bufferedUpdate(doFinal, ib, ob);

				int delta = ob.wIndex() - out.position();
				in.position(ib.rIndex);
				out.position(ob.wIndex());
				return delta;
			}
		}
		return -1;
	}
	private ByteList prev = ByteList.EMPTY;
	// 当你用了下面的Public crypt函数你就不要用这些了...
	private void bufferedUpdate(boolean doFinal, DynByteBuf ib, DynByteBuf ob) {
		try {
			handlePrev: {
				while (prev.isReadable()) {
					if (!ib.isReadable()) {
						ib = prev;
						break handlePrev;
					}

					prev.put(ib.get());
					crypt(prev, ob);
				}
				prev.clear();
			}

			if (doFinal) {
				cryptFinal(ib, ob);
				if (ib.isReadable()) throw new ProviderException("cryptFinal未处理输入");
			} else {
				crypt(ib, ob);

				if (ib.isReadable()) {
					if (prev.capacity() == 0) prev = new ByteList();
					prev.put(ib);
				}
			}
		} catch (Exception e) { Helpers.athrow(e); }
	}
	// endregion

	public void init(int mode, byte[] key) throws InvalidKeyException {
		try {
			init(mode, key, null, null);
		} catch (InvalidAlgorithmParameterException e) {
			throw new ProviderException("Unexpected error", e);
		}
	}
	public abstract void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException;

	public void insertAAD(DynByteBuf aad) {
		throw new UnsupportedOperationException("The underlying Cipher implementation does not support this method");
	}
	public abstract void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException;
	public void cryptOneBlock(DynByteBuf in, DynByteBuf out) {
		throw new IllegalStateException(getClass().getName() + " is not a block cipher");
	}
	public final void cryptFinal(DynByteBuf in, DynByteBuf out) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		crypt(in, out);
		cryptFinal1(in, out);
	}
	protected void cryptFinal1(DynByteBuf in, DynByteBuf out) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {}

	public int cryptInline(DynByteBuf in, int length) {
		DynByteBuf inCopy = in.slice(in.rIndex, length);

		int pos = in.wIndex();
		in.wIndex(in.rIndex);
		try {
			cryptFinal(inCopy, in);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return in.wIndex() - pos;
	}
}