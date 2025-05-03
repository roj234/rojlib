package roj.archive.qz;

import roj.archive.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Roj234
 * @since 2023/5/23 14:32
 */
public final class Deflate extends QZCoder {
	public Deflate(int level) { this.level = level; }
	private final int level;

	private static final byte[] ID = {4,1,8};
	byte[] id() {return ID;}

	private final AtomicReference<Deflater> def = new AtomicReference<>();

	public OutputStream encode(OutputStream out) throws IOException {
		Deflater def = this.def.getAndSet(null);
		if (def == null) def = new Deflater(level, true);
		return new DeflaterOutputStream(out, def) {
			public void close() throws IOException {
				super.close();

				if (def != null) {
					synchronized (this) {
						if (def != null) {
							Deflater prev = Deflate.this.def.getAndSet(def);
							def = null;
							if (prev != null) prev.end();
						}
					}
				}
			}
		};
	}
	public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException { useMemory(memoryLimit, 64); return ZipFile.getCachedInflater(in); }
}