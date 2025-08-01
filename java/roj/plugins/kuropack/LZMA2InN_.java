package roj.plugins.kuropack;

import roj.archive.xz.LZMA2InputStream;
import roj.archive.xz.lz.LZDecoder;
import roj.archive.xz.lzma.LZMADecoder;
import roj.archive.xz.rangecoder.RangeDecoder;
import roj.asmx.injector.Inject;
import roj.asmx.injector.Shadow;
import roj.asmx.injector.Weave;

import java.io.DataInputStream;
import java.io.IOException;

@Weave(target = LZMA2InputStream.class)
abstract class LZMA2InN_ {
	@Shadow
	private DataInputStream in;

	@Shadow
	private LZDecoder lz;
	@Shadow
	private RangeDecoder rc;
	@Shadow
	private LZMADecoder lzma;

	@Shadow
	private int uncompressedSize;
	@Shadow
	private byte state;

	@Shadow
	public void close() {}

	@Shadow
	private void nextChunk() {}

	@Inject("read")
	public int read(byte[] buf, int off, int len) throws IOException {
		if (in == null) throw new IOException("Stream closed");

		if (state == -1) return -1;

		try {
			int read = 0;

			while (len > 0) {
				if (uncompressedSize <= 0) {
					nextChunk();
					if (state == -1) return read == 0 ? -1 : read;
				}

				int copySizeMax = Math.min(uncompressedSize, len);

				if (state == 0) {
					lz.setLimit(copySizeMax);
					lzma.decode();
				} else {
					lz.copyUncompressed(in, copySizeMax);
				}

				int copiedSize = lz.flush(buf, off);
				off += copiedSize;
				len -= copiedSize;
				read += copiedSize;
				uncompressedSize -= copiedSize;

				if (uncompressedSize == 0) if (!rc.isFinished() || lz.hasPending()) throw new IllegalStateException();
			}

			return read;
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}
	@Inject(at = Inject.At.REMOVE, value = "read")
	public abstract int read(long addr, int len) throws IOException;
	@Inject(at = Inject.At.REMOVE, value = "read0")
	public abstract int read0(Object buf, long addr, int len) throws IOException;
}