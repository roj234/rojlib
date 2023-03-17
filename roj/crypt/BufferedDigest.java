package roj.crypt;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * @author solo6975
 * @since 2021/10/3 15:06
 */
public abstract class BufferedDigest extends MessageDigest implements Cloneable {
	protected final int[] intBuffer;
	private final byte[] byteBuffer;
	protected int bufOff;
	protected boolean LE;

	protected BufferedDigest(String algorithm) {
		super(algorithm);
		this.intBuffer = new int[engineGetIntBufferLength()];
		this.byteBuffer = new byte[intBuffer.length << 2];
	}

	protected BufferedDigest(String algorithm, int byteLen) {
		super(algorithm);
		this.intBuffer = new int[engineGetIntBufferLength()];
		this.byteBuffer = new byte[byteLen];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected abstract int engineDigest(byte[] buf, int offset, int len) throws DigestException;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int engineGetDigestLength() {
		return engineGetIntBufferLength() << 2;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void engineUpdate(byte input) {
		byteBuffer[bufOff++] = input;
		if (bufOff == byteBuffer.length) {
			Conv.b2i(byteBuffer, 0, byteBuffer.length, intBuffer, 0);
			engineIntDigest();
			bufOff = 0;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void engineUpdate(byte[] input, int off, int len) {
		int max = byteBuffer.length;
		int bufOff = this.bufOff;
		if (bufOff != 0) {
			int require = Math.min(max - bufOff, len);
			System.arraycopy(input, off, byteBuffer, bufOff, require);
			if ((this.bufOff = (bufOff + require)) < max) {
				return;
			}
			if (LE) {Conv.b2i_LE(byteBuffer, 0, max, intBuffer, 0);} else Conv.b2i(byteBuffer, 0, max, intBuffer, 0);
			engineIntDigest();
			this.bufOff = 0;
			off += require;
			len -= require;
		}

		int r = len & (max - 1);
		len -= r;
		for (len += off; off < len; off += max) {
			if (LE) {Conv.b2i_LE(input, off, max, intBuffer, 0);} else Conv.b2i(input, off, max, intBuffer, 0);
			engineIntDigest();
		}
		this.bufOff = r;
		if (r > 0) {
			System.arraycopy(input, off, byteBuffer, 0, r);
		}
	}

	protected abstract void engineIntDigest();

	protected abstract int engineGetIntBufferLength();

	protected final void engineFinish() {
		int bo = bufOff;
		if (bo != 0) {
			byte[] bb = byteBuffer;
			int[] ib = intBuffer;
			Conv.b2i(bb, 0, bo, ib, 0);

			int j = bufOff >> 2;
			while (j < bb.length >> 2) {
				ib[j++] = 0;
			}

			engineIntDigest();
			bufOff = 0;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected byte[] engineDigest() {
		try {
			byte[] buf = new byte[engineGetDigestLength()];
			engineDigest(buf, 0, buf.length);
			return buf;
		} catch (DigestException e) {
			throw new IllegalStateException("Should not happen", e);
		}
	}

	@Override
	public String toString() {
		return getAlgorithm() + " Message Digest from RojLib";
	}
}
