package roj.archive.qz;

import roj.archive.xz.MemoryLimitException;
import roj.collect.HashMap;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2023/3/14 8:04
 */
public abstract class QZCoder {
	private static final HashMap<ByteList, QZCoder> coders = new HashMap<>();
	private static void reg(QZCoder c) { coders.put(ByteList.wrap(c.id()), c); }

	QZCoder factory() {return this;}
	abstract byte[] id();

	public OutputStream encode(OutputStream out) throws IOException { throw new UnsupportedOperationException(); }
	public abstract InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException;
	protected static void useMemory(AtomicInteger memoryLimit, int memoryUsage) throws MemoryLimitException {
		if (memoryLimit.addAndGet(-memoryUsage) < 0) throw new MemoryLimitException(memoryUsage, memoryLimit.get()+memoryUsage);
	}

	@Override
	public String toString() { return getClass().getSimpleName(); }

	void writeOptions(DynByteBuf buf) {}
	void readOptions(DynByteBuf buf, int length) throws IOException {}

	public static QZCoder create(DynByteBuf buf) {
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

		var coder = coders.get(buf);
		if (coder == null) return new Unknown(buf.toByteArray());
		return coder.factory();
	}
}