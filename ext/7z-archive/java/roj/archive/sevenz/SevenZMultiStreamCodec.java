package roj.archive.sevenz;

import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/5/29 0:38
 */
public abstract class SevenZMultiStreamCodec extends SevenZCodec {
	public abstract int getInCount();
	public abstract int getOutCount();

	@Deprecated
	public final OutputStream encode(OutputStream out) { throw new UnsupportedOperationException(getClass().getSimpleName() + " 是多流编码"); }
	@Deprecated
	public final InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " 是多流编码");
	}

	public OutputStream[] encodeMulti(OutputStream[] out) throws IOException { super.encode(null); return Helpers.maybeNull(); }
	public abstract InputStream[] decodeMulti(InputStream[] in, long[] uncompressedSize, int sizeBegin, AtomicInteger memoryLimit) throws IOException;
}