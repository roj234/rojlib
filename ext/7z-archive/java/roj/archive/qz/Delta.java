package roj.archive.qz;

import roj.archive.qz.bcj.DeltaFilter;
import roj.crypt.CipherInputStream;
import roj.crypt.CipherOutputStream;
import roj.util.DynByteBuf;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/5/28 11:16
 */
public final class Delta extends QZCoder {
	private short distance;
	public Delta() { distance = 1; }
	public Delta(int distance) { this.distance = (short) distance; }

	QZCoder factory() {return new Delta();}
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Delta delta)) return false;

		return distance == delta.distance;
	}
	@Override
	public int hashCode() {return distance;}

	private static final byte[] id = {3};
	byte[] id() {return id;}

	public OutputStream encode(OutputStream out) { return new CipherOutputStream(out, new DeltaFilter(true, distance)); }
	public InputStream decode(InputStream in, byte[] p, long u, AtomicInteger memoryLimit) { return new CipherInputStream(in, new DeltaFilter(false, distance)); }

	public String toString() {return "delta:"+distance;}

	void readOptions(DynByteBuf buf, int length) { if (length > 0) distance = (short) (buf.readUnsignedByte()+1); }
	void writeOptions(DynByteBuf buf) { if (distance > 1) buf.put(distance-1); }
}