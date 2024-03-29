package roj.archive.qz;

import roj.archive.qz.bcj.ARMFilter;
import roj.archive.qz.bcj.ARMThumbFilter;
import roj.archive.qz.bcj.X86Filter;
import roj.crypt.CipherInputStream;
import roj.crypt.CipherOutputStream;
import roj.crypt.RCipherSpi;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2023/3/16 0016 0:06
 */
public final class BCJ extends QZCoder {
	public static final BCJ X86 = new BCJ(1), ARM = new BCJ(5), ARM_THUMB = new BCJ(7);
	private final byte[] id;

	private BCJ(int type) { id = new byte[] {3,3,(byte)type,(byte)(type==1?3:1)}; }

	QZCoder factory() { return this; }
	byte[] id() { return id; }

	public OutputStream encode(OutputStream out) { return new CipherOutputStream(out, bcj(true)); }
	public InputStream decode(InputStream in, byte[] p, long u, int m) { return new CipherInputStream(in, bcj(false)); }

	private RCipherSpi bcj(boolean encode) {
		switch (id[2]) {
			default:
			case 1: return new X86Filter(encode,0);
			case 5: return new ARMFilter(encode,0);
			case 7: return new ARMThumbFilter(encode,0);
		}
	}
}
