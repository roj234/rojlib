package roj.archive.sevenz;

import org.jetbrains.annotations.Range;
import roj.archive.sevenz.bcj.DeltaFilter;
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
@SevenZCodecExtension("03")
public final class Delta extends SevenZCodec {
	private static final byte[] id = {3};
	static {register(id, Delta::new);}

	private final short distance;
	public Delta(@Range(from = 1, to = 256) int distance) {this.distance = (short) distance;}
	private Delta(DynByteBuf properties) {distance = !properties.isReadable() ? 1 : (short) (properties.readUnsignedByte() + 1);}

	public byte[] id() {return id;}

	public OutputStream encode(OutputStream out) { return new CipherOutputStream(out, new DeltaFilter(true, distance)); }
	public InputStream decode(InputStream in, byte[] p, long u, AtomicInteger memoryLimit) { return new CipherInputStream(in, new DeltaFilter(false, distance)); }

	public void writeOptions(DynByteBuf props) { if (distance > 1) props.put(distance-1); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Delta delta)) return false;

		return distance == delta.distance;
	}
	@Override
	public int hashCode() {return distance;}

	public String toString() {return "Delta:"+distance;}
}