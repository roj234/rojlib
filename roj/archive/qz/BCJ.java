package roj.archive.qz;

import roj.archive.qz.bcj.ARMFilter;
import roj.archive.qz.bcj.ARMThumbFilter;
import roj.archive.qz.bcj.X86Filter;
import roj.crypt.CipheR;
import roj.crypt.CipherInputStream;
import roj.crypt.CipherOutputStream;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2023/3/16 0016 0:06
 */
public final class BCJ extends QZCoder {
	static final byte X86 = 1, ARM = 5, ARM_THUMB = 7;
	private final byte[] id;

	public BCJ(byte type) { id = new byte[] {3,3,type,(byte)(type==1?3:1)}; }

	QZCoder factory() { return this; }
	byte[] id() { return id; }

	public OutputStream encode(OutputStream out) { return new CipherOutputStream(out, bcj(true)); }
	public InputStream decode(InputStream in, byte[] p, long u, int m) { return new CipherInputStream(in, bcj(false)); }

	private CipheR bcj(boolean encode) {
		switch (id[2]) {
			case X86: return new X86Filter(encode,0);
			case ARM: return new ARMFilter(encode,0);
			case ARM_THUMB: return new ARMThumbFilter(encode,0);
		}
		throw new IllegalArgumentException(String.valueOf(id[2]));
	}

}
