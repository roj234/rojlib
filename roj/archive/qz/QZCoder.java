package roj.archive.qz;

import roj.collect.MyHashMap;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/3/14 0014 8:04
 */
public abstract class QZCoder {
	private static final MyHashMap<ByteList, QZCoder> coders = new MyHashMap<>();
	private static void reg(QZCoder c) { coders.put(ByteList.wrap(c.id()), c); }

	abstract QZCoder factory();
	abstract byte[] id();

	public OutputStream encode(OutputStream out) throws IOException {
		throw new UnsupportedOperationException();
	}
	public abstract InputStream decode(InputStream in, byte[] password, long uncompressedSize, final int maxMemoryLimitInKb) throws IOException;

	void writeOptions(DynByteBuf buf) {}
	void readOptions(DynByteBuf buf, int length) throws IOException {}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	static QZCoder create(ByteList buf, int len) {
		if (coders.isEmpty()) {
			// <clinit> order

			// PPMd: 4,1,8
			// Bzip2: 4,2,2
			reg(new Copy());
			reg(new AESCrypt());
			reg(new LZMA(true));
			reg(new LZMA2(true));
			reg(new BCJ(BCJ.X86));
			reg(new BCJ(BCJ.ARM));
			reg(new BCJ(BCJ.ARM_THUMB));
		}
		buf = buf.slice(len);
		QZCoder coder = coders.get(buf);
		return coder == null ? new Unknown(buf.toByteArray()) : coder.factory();
	}

	// 黑科技.jpg
	public final int hashCode() {
		return Arrays.hashCode(id());
	}
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
