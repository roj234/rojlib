package roj.archive.qz;

import roj.archive.qz.bcj.DeltaFilter;
import roj.crypt.CipherInputStream;
import roj.crypt.CipherOutputStream;
import roj.util.DynByteBuf;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2023/5/28 11:16
 */
public final class Delta extends QZCoder {
	private short distance;
	public Delta() { distance = 1; }
	public Delta(int distance) { this.distance = (short) distance; }

	QZCoder factory() { return new Delta(); }
	private static final byte[] id = {3};
	byte[] id() { return id; }

	public OutputStream encode(OutputStream out) { return new CipherOutputStream(out, new DeltaFilter(true, distance)); }
	public InputStream decode(InputStream in, byte[] p, long u, int m) { return new CipherInputStream(in, new DeltaFilter(false, distance)); }

	public String toString() { return "delta:"+distance; }

	void readOptions(DynByteBuf buf, int length) { if (length > 0) distance = (short) (buf.readUnsignedByte()+1); }
	void writeOptions(DynByteBuf buf) { if (distance > 1) buf.put(distance-1); }
}
