package roj.crypt;

import roj.util.Helpers;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * PHBC 自己想的 要是有人说没啥用或者验证下也可以
 * CBC-like用到哈希里
 *
 * @author solo6975
 * @since 2022/2/12 19:50
 */
public final class PartialHashBlockChain extends MessageDigest {
	private final MessageDigest m;
	private final byte[] buf;
	private final int block;

	public PartialHashBlockChain(MessageDigest owner, int len) {
		super(owner.getAlgorithm() + "-PHBC");
		this.m = owner;
		this.block = owner.getDigestLength();
		this.buf = new byte[len];
	}

	@Override
	protected void engineUpdate(byte input) {
		m.update(input);
	}

	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		m.update(input, offset, len);
	}

	@Override
	protected byte[] engineDigest() {
		byte[] buf = this.buf;
		try {
			for (int i = block; i < buf.length; i++) {
				buf[i] = 0;
			}

			m.digest(buf, 0, block);

			int i = block;
			while (buf.length - i >= block) {
				m.update(buf, 0, buf.length);
				m.digest(buf, i, block);
				i += block;
			}
			if (buf.length - i > 0) {
				m.update(buf, i, buf.length - i);
				m.digest(buf, buf.length - block, block);
			}
		} catch (DigestException e) {
			Helpers.athrow(e);
		}

		return buf;
	}

	@Override
	protected void engineReset() {
		m.reset();
	}
}
