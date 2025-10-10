package roj.archive.qz;

import roj.archive.xz.MemoryLimitException;
import roj.asmx.AnnotationRepoManager;
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
	public static synchronized void register(QZCoder c) { coders.put(ByteList.wrap(c.id()), c); }

	protected QZCoder factory() {return this;}
	public abstract byte[] id();

	public OutputStream encode(OutputStream out) throws IOException { throw new UnsupportedOperationException(); }
	public abstract InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException;
	protected static void useMemory(AtomicInteger memoryLimit, int memoryUsage) throws MemoryLimitException {
		if (memoryLimit.addAndGet(-memoryUsage) < 0) throw new MemoryLimitException(memoryUsage, memoryLimit.get()+memoryUsage);
	}

	@Override
	public String toString() { return getClass().getSimpleName(); }

	public void writeOptions(DynByteBuf buf) {}
	public void readOptions(DynByteBuf buf, int length) throws IOException {}

	public static QZCoder create(DynByteBuf buf) {
		if (coders.isEmpty()) {
			synchronized (coders) {
				if (coders.isEmpty()) {
					register(Copy.INSTANCE);
					register(new QzAES());
					register(new Deflate(Deflater.DEFAULT_COMPRESSION));
					register(new LZMA(true));
					register(new LZMA2(true));
					register(BCJ.X86);
					register(BCJ.ARM);
					register(BCJ.ARM_THUMB);
					register(new Delta());
					register(new BCJ2());
					AnnotationRepoManager.initializeAnnotatedType("roj/archive/qz/QZCustomCoder", QZCoder.class.getClassLoader(), true);
				}
			}
		}

		var coder = coders.get(buf);
		if (coder == null) return new Unknown(buf.toByteArray());
		return coder.factory();
	}
}