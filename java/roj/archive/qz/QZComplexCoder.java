package roj.archive.qz;

import roj.archive.qz.xz.UnsupportedOptionsException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2023/5/29 0029 0:38
 */
public abstract class QZComplexCoder extends QZCoder {
	public abstract int useCount();
	public abstract int provideCount();

	public OutputStream[] complexEncode(OutputStream[] out) throws IOException { throw new UnsupportedOperationException(); }
	public abstract InputStream[] complexDecode(InputStream[] in, long[] uncompressedSize, int sizeBegin) throws IOException;
	@Deprecated
	public final InputStream decode(InputStream in, byte[] password, long uncompressedSize, int maxMemoryLimitInKb) throws IOException { throw new UnsupportedOptionsException("complex codec"); }
}
