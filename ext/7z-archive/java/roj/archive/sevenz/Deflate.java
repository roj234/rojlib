package roj.archive.sevenz;

import roj.archive.zip.InflateInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Roj234
 * @since 2023/5/23 14:32
 */
@SevenZCodecExtension("040108")
public final class Deflate extends SevenZCodec {
	private static final byte[] ID = {4,1,8};
	static {register(new Deflate(-1));}

	public Deflate(int level) { this.level = level; }
	private final int level;

	public byte[] id() {return ID;}

	public OutputStream encode(OutputStream out) throws IOException {
		return new DeflaterOutputStream(out, new Deflater(level, true)) {
			public void close() throws IOException {
				super.close();
				def.end();
			}
		};
	}
	public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
		checkMemoryUsage(memoryLimit, 64);
		return InflateInputStream.getInstance(in);
	}
}