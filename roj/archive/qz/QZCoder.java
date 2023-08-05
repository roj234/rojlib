package roj.archive.qz;

import roj.collect.MyHashMap;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2023/3/14 0014 8:04
 */
public abstract class QZCoder {
	private static final MyHashMap<ByteList, QZCoder> coders = new MyHashMap<>();
	private static void reg(QZCoder c) { coders.put(ByteList.wrap(c.id()), c); }

	abstract QZCoder factory();
	abstract byte[] id();

	public OutputStream encode(OutputStream out) throws IOException { throw new UnsupportedOperationException(); }
	public abstract InputStream decode(InputStream in, byte[] password, long uncompressedSize, final int maxMemoryLimitInKb) throws IOException;

	@Override
	public String toString() { return getClass().getSimpleName(); }

	void writeOptions(DynByteBuf buf) {}
	void readOptions(DynByteBuf buf, int length) throws IOException {}

	public static QZCoder create(ByteList buf, int len) {
		if (coders.isEmpty()) {
			synchronized (coders) {
				if (coders.isEmpty()) {
					reg(Copy.INSTANCE);
					reg(new QzAES());
					reg(new Deflate(Deflater.DEFAULT_COMPRESSION));
					reg(new LZMA(true));
					reg(new LZMA2(true));
					reg(BCJ.X86);
					reg(BCJ.ARM);
					reg(BCJ.ARM_THUMB);
					reg(new Delta());
					reg(new BCJ2());
				}
			}
		}

		buf = buf.slice(len);
		QZCoder coder = coders.get(buf);
		return coder == null ? new Unknown(buf.toByteArray()) : coder.factory();
	}

	// 黑科技.jpg
	public final int hashCode() { return Arrays.hashCode(id()); }
	public final boolean equals(Object o) {
		if (this == o) return true;

		if (!(o instanceof ByteList)) return false;
		ByteList b = ((ByteList) o);

		byte[] list = b.list;
		byte[] id = id();
		int off = b.relativeArrayOffset();
		int len = b.readableBytes();
		for (int i = 0; i < len; i++) {
			if (list[off] != id[i]) return false;
		}
		return true;
	}
}
