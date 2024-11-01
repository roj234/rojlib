package roj.net.http.server;

import roj.io.IOUtil;
import roj.io.LimitInputStream;
import roj.io.source.FileSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2024/11/28 0028 6:21
 */
public class GZFileInfo implements FileInfo {
	private final File file;
	private final int modTime, skipBytes, compSize, dataCrc, uncompSize;

	private final static int FHCRC = 2, FEXTRA = 4, FNAME = 8, FCOMMENT = 16; // File comment

	public GZFileInfo(File file) throws IOException {
		try (var source = new FileSource(file, false)) {
			var in = source.asDataInput();

			// Check header magic
			if (in.readUnsignedShort() != 0x1F8B) throw new ZipException("Not in GZIP format");
			// Check compression method
			if (in.readUnsignedByte() != 8) throw new ZipException("Unsupported compression method");

			int flg = in.readUnsignedByte();
			modTime = Integer.reverseBytes(in.readInt());
			in.skipBytes(2);

			int n = 2 + 2 + 6;

			// extra
			if ((flg & FEXTRA) != 0) {
				int m = Short.reverseBytes(in.readShort()) & 0xFF;
				in.skipBytes(m);
				n += m + 2;
			}

			// file name
			if ((flg & FNAME) != 0) {
				do {
					n++;
				} while (in.readByte() != 0);
			}

			// comment
			if ((flg & FCOMMENT) != 0) {
				do {
					n++;
				} while (in.readByte() != 0);
			}

			// header CRC
			if ((flg & FHCRC) != 0) {
				n += 2;
			}

			skipBytes = n;

			source.seek(source.length() - 8);
			compSize = (int) (source.length() - n - 8);
			dataCrc = Integer.reverseBytes(in.readInt());
			uncompSize = Integer.reverseBytes(in.readInt());
		}
		this.file = file;
	}

	@Override
	public int stats() {return FILE_RA | FILE_DEFLATED;}

	@Override
	public long length(boolean deflated) {return deflated ? compSize : uncompSize;}

	@Override
	public InputStream get(boolean deflated, long offset) throws IOException {
		var in = new FileInputStream(file);
		try {
			if (deflated) {
				IOUtil.skipFully(in, skipBytes + offset);
				return new LimitInputStream(in, compSize);
			} else {
				return new GZIPInputStream(in);
			}
		} catch (Exception e) {
			IOUtil.closeSilently(in);
			throw e;
		}
	}

	@Override
	public long lastModified() {return modTime * 1000L;}
}
