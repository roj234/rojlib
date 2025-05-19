package roj.archive.zip;

import org.jetbrains.annotations.NotNull;
import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveWriter;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.io.source.CompositeSource;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static roj.archive.zip.ZipArchive.*;

/**
 * @author solo6975
 * @since 2021/10/5 13:54
 */
public class ZipFileWriter extends OutputStream implements ArchiveWriter {
	private Source file;
	private final Deflater def;
	private final ByteList CENs = new ByteList(), buf = new ByteList();

	private boolean finish;
	private int fileCount;
	private byte[] comment = ArrayCache.BYTES;

	public ZipFileWriter(File file) throws IOException { this(file, Deflater.DEFAULT_COMPRESSION, 0); }
	public ZipFileWriter(File file, int compression, long splitSize) throws IOException {
		this.file = splitSize != 0 ? CompositeSource.fixed(file, splitSize) : new FileSource(file);
		this.file.seek(0);
		this.def = new Deflater(compression, true);
	}
	public ZipFileWriter(Source file, int compression) throws IOException {
		this.file = file;
		this.file.seek(0);
		this.def = new Deflater(compression, true);
	}

	public void setComment(String comment) { this.comment = comment.getBytes(StandardCharsets.UTF_8); }
	public void setComment(byte[] comment) { this.comment = comment; }

	public void writeNamed(String name, ByteList b) throws IOException { writeNamed(name, b, ZipEntry.DEFLATED); }
	public void writeNamed(String name, ByteList b, int method) throws IOException { writeNamed(name, b, method, System.currentTimeMillis()); }
	public void writeNamed(String name, ByteList b, int method, long modTime) throws IOException {
		if (entry != null) closeEntry();
		if (name.endsWith("/")) throw new ZipException("目录不是空的: "+name);

		int crc = CRC32.crc32(b.list, b.arrayOffset()+b.rIndex, b.readableBytes());

		int time = ZEntry.java2DosTime(modTime);
		ByteList buf = this.buf;
		buf.clear();
		buf.putInt(HEADER_LOC)
		   .putShortLE(20)
		   .putShortLE(2048)
		   .putShortLE(method)
		   .putIntLE(time)
		   .putIntLE(crc)
		   .putIntLE(b.readableBytes()) // cSize
		   .putIntLE(b.readableBytes())
		   .putShortLE(DynByteBuf.byteCountUTF8(name))
		   .putShortLE(0)
		   .putUTFData(name);
		long beginOffset = file.position();
		file.write(buf.list, 0, buf.wIndex());
		buf.clear();

		int cSize;
		if (method == ZipEntry.DEFLATED) {
			Deflater def = this.def;
			def.setInput(b.list, b.arrayOffset()+b.rIndex, b.readableBytes());
			def.finish();
			buf.ensureCapacity(8192);
			while (!def.finished()) {
				int off = this.def.deflate(buf.list, 0, 8192);
				file.write(buf.list, 0, off);
			}

			// obviously < 2G
			cSize = (int) def.getBytesWritten();
			def.reset();

			long curr = file.position();
			file.seek(beginOffset + 18);
			file.write(buf.putIntLE(cSize)); buf.clear();
			file.seek(curr);
		} else {
			file.write(b.list, b.arrayOffset()+b.rIndex, cSize = b.readableBytes());
		}

		boolean attrZip64 = beginOffset >= U32_MAX;
		buf.putInt(HEADER_CEN)
		   .putShortLE(attrZip64 ? 45 : 20)
		   .putShortLE(attrZip64 ? 45 : 20)
		   .putShortLE(2048)
		   .putShortLE(method)
		   .putIntLE(time)
		   .putIntLE(crc)
		   .putIntLE(cSize)
		   .putIntLE(b.readableBytes())
		   .putShortLE(DynByteBuf.byteCountUTF8(name))
		   .putShortLE(attrZip64 ? 10 : 0)
		   .putShortLE(0)
		   .putIntLE(0) // 四个short 0
		   .putIntLE(0)
		   .putIntLE((int) (attrZip64 ? U32_MAX : beginOffset))
		   .putUTFData(name);
		if (attrZip64) {
			buf.putShortLE(1).putShortLE(8).putLongLE(entryBeginOffset);
		}
		CENs.put(buf);
		fileCount++;
		buf.clear();
	}

	@Override
	public void copy(ArchiveFile o, ArchiveEntry e) throws IOException {
		ZipFile owner = (ZipFile) o;
		ZEntry entry = (ZEntry) e;

		if (this.entry != null) closeEntry();
		long entryBeginOffset = file.position();

		// 20250221 fixed 文件中不知道长度的Entry但是经过读取已经知道了，所以复制之前需要重写CEntry
		owner.validateEntry(entry);
		if ((entry.flags & GP_HAS_EXT) != 0 && entry.getCompressedSize() != 0) {
			entry = entry.clone();
			entry.flags &= ~GP_HAS_EXT;
			ZipArchive.writeLOC(file, buf, entry);
			file.put(owner.source(), entry.getOffset(), entry.getCompressedSize());
		} else {
			file.put(owner.source(), entry.startPos(), entry.endPos() - entry.startPos());
		}

		long delta = entryBeginOffset - entry.startPos();
		entry.offset += delta;
		buf.clear();
		ZipArchive.writeCEN(buf, entry);
		entry.offset -= delta;
		CENs.put(buf);
		fileCount++;
		buf.clear();
	}

	private ZEntry entry;
	private long entryBeginOffset, dataBeginOffset;
	private int crc = CRC32.initial;

	public void beginEntry(ZipEntry ze) throws IOException {
		ZEntry entry = new ZEntry(ze.getName());
		entry.setMethod(ze.getMethod());
		entry.setModificationTime(ze.getTime());
		beginEntry(entry, false);
	}
	@Override
	public void beginEntry(ArchiveEntry e) throws IOException {
		beginEntry((ZEntry) e, false);
	}
	public void beginEntry(ZEntry ze, boolean large) throws IOException {
		if (entry != null) closeEntry();
		entry = ze;
		// noinspection all
		if (ze.getMethod() == -1) ze.setMethod(ZipEntry.STORED);

		entryBeginOffset = file.position();

		if (large) ze.cSize = ze.uSize = U32_MAX;
		if (ze.nameBytes == null) {
			entry.flags |= GP_UTF;
			entry.nameBytes = IOUtil.encodeUTF8(entry.name);
		}

		ZipArchive.writeLOC(file, buf, ze);
		dataBeginOffset = file.position();
	}

	private byte[] b1;
	@Override
	@Deprecated
	public final void write(int b) throws IOException {
		if (b1 == null) b1 = new byte[1];
		b1[0] = (byte) b;
		write(b1,0,1);
	}

	@Override
	public void write(@NotNull byte[] b, int off, int len) throws IOException {
		if (entry == null) throw new ZipException("Entry closed");

		crc = CRC32.update(crc, b, off, len);

		if (entry.getMethod() == ZipEntry.STORED) {
			file.write(b, off, len);
		} else {
			Deflater def = this.def;

			if (!def.finished()) {
				def.setInput(b, off, len);

				ByteList buf = this.buf;
				buf.ensureCapacity(1024);

				byte[] list = buf.list;
				while (!def.needsInput()) {
					off = this.def.deflate(list);
					file.write(list, 0, off);
				}
			} else {
				throw new ZipException("Entry asynchronously closed");
			}
		}
	}

	public void closeEntry() throws IOException {
		if (entry == null) return;
		Source f = this.file;

		if (entry.getMethod() != ZipEntry.STORED) {
			Deflater def = this.def;
			def.finish();

			ByteList buf = this.buf;
			buf.clear();
			buf.ensureCapacity(1024);
			byte[] list = buf.list;

			while (!def.finished()) {
				f.write(list, 0, this.def.deflate(list));
			}
		}

		long pos = f.position();

		long cSize = pos - dataBeginOffset;
		long uSize = entry.getMethod() == ZipEntry.STORED ? cSize : def.getBytesRead();
		def.reset();

		boolean large = cSize >= U32_MAX || uSize >= U32_MAX;
		if (large && entry.cSize < U32_MAX && entry.uSize < U32_MAX)
			throw new ZipException("文件过大(4GB), 你要在beginEntry中打开zip64 (默认关闭以节约空间)");

		entry.offset = dataBeginOffset;
		entry.cSize = cSize;
		entry.uSize = uSize;
		entry.crc32 = crc;
		crc = CRC32.initial;

		int off = CENs.wIndex();
		ZipArchive.writeCEN(CENs, entry);
		fileCount++;

		f.seek(entryBeginOffset+14);
		f.write(CENs.list, off+16, 12);

		if (large) {
			f.seek(entryBeginOffset+34+entry.nameBytes.length);
			buf.clear();
			buf.putLongLE(uSize).putLongLE(cSize);
			f.write(buf);
		}

		f.seek(pos);

		entry = null;
	}

	public void finish() throws IOException {
		synchronized (this) {
			if (finish) return;
			finish = true;
		}

		if (entry != null) closeEntry();

		def.end();

		Source f = file;
		long cDirOffset = f.position();
		f.write(CENs);
		CENs._free();

		long cDirLen = f.position()-cDirOffset;
		buf.clear();
		ZipArchive.writeEND(buf, cDirOffset, cDirLen, fileCount, comment, f.position());
		f.write(buf.list, 0, buf.wIndex());

		if (f.length() != f.position()) // truncate
			f.setLength(f.position());
		f.close();

		buf._free();
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			finish();
		} finally {
			file.close();
		}
	}
}