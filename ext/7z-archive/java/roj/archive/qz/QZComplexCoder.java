package roj.archive.qz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/5/29 0:38
 */
public abstract class QZComplexCoder extends QZCoder {
	public abstract int useCount();
	public abstract int provideCount();

	public OutputStream[] complexEncode(OutputStream[] out) throws IOException { throw new UnsupportedOperationException("complex codec"); }
	public abstract InputStream[] complexDecode(InputStream[] in, long[] uncompressedSize, int sizeBegin, AtomicInteger memoryLimit) throws IOException;
	@Deprecated
	public final InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException { throw new UnsupportedOperationException("complex codec"); }
}