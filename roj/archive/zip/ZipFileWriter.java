package roj.archive.zip;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveWriter;
import roj.collect.MyHashSet;
import roj.io.source.BufferedSource;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.io.source.SplittedSource;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static roj.archive.zip.ZipArchive.*;

/**
 * Zip [File] Writer
 *
 * @author solo6975
 * @version 1.0
 * @since 2021/10/5 13:54
 */
public class ZipFileWriter extends OutputStream implements ArchiveWriter {
	public static final int CHECK_DUPLICATE_FILE = 1, SPLIT_FILE = 2;

	private Source file;
	private final Deflater def;
	private final ByteList CENs;
	private final ByteList buf;
	private final CRC32 crc;
	private final MyHashSet<String> duplicate;

	private boolean finish;
	private final END eof;

	public ZipFileWriter(File file) throws IOException {
		this(new FileSource(file), Deflater.DEFAULT_COMPRESSION, false, 0);
	}

	public ZipFileWriter(File file, boolean checkDuplicate) throws IOException {
		this(new FileSource(file), Deflater.DEFAULT_COMPRESSION, checkDuplicate, 0);
	}

	public ZipFileWriter(Source file, int compressionLevel, boolean checkDuplicate, int splitSize) throws IOException {
		this.file = file;
		this.file.seek(0);
		this.def = new Deflater(compressionLevel, true);
		this.CENs = new ByteList();
		this.buf = new ByteList();
		this.crc = new CRC32();
		this.eof = new END();
		this.duplicate = checkDuplicate ? new MyHashSet<>() : null;

		if (splitSize != 0) {
			this.file = BufferedSource.autoClose(new SplittedSource((FileSource) file, splitSize));
			buf.clear();
		}
	}

	public ArchiveEntry createEntry(String fileName) { return new ZEntry(fileName); }

	public void setComment(String comment) {
		eof.setComment(comment);
	}
	public void setComment(byte[] comment) {
		eof.setComment(comment);
	}

	public void writeNamed(String name, ByteList b) throws IOException {
		writeNamed(name, b, ZipEntry.DEFLATED);
	}
	public void writeNamed(String name, ByteList b, int method) throws IOException {
		if (entry != null) closeEntry();
		if (name.endsWith("/")) throw new ZipException("ZipEntry couldn't be directory");
		if (duplicate != null && !duplicate.add(name)) throw new ZipException("Duplicate entry " + name);

		crc.update(b.list, b.arrayOffset()+b.rIndex, b.readableBytes());

		int time = ZEntry.java2DosTime(System.currentTimeMillis());
		ByteList buf = this.buf;
		buf.clear();
		buf.putInt(HEADER_LOC)
		   .putShortLE(20)
		   .putShortLE(2048)
		   .putShortLE(method)
		   .putIntLE(time)
		   .putIntLE((int) crc.getValue())
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
		   .putIntLE((int) crc.getValue())
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
		eof.cDirTotal++;
		buf.clear();

		crc.reset();
	}

	@Override
	public void copy(ArchiveFile o, ArchiveEntry e) throws IOException {
		ZipArchive owner = (ZipArchive) o;
		ZEntry entry = (ZEntry) e;

		if (this.entry != null) closeEntry();
		long entryBeginOffset = file.position();
		if (duplicate != null && !duplicate.add(entry.name)) throw new ZipException("Duplicate entry " + entry.name);
		if (file.hasChannel() & owner.getFile().hasChannel()) {
			FileChannel myCh = file.channel();
			owner.getFile().channel().transferTo(entry.startPos(), entry.endPos() - entry.startPos(), myCh);
		} else {
			Source src = owner.getFile();
			src.seek(entry.startPos());
			int max = Math.max(4096, buf.list.length);
			buf.ensureCapacity(max);

			byte[] list = buf.list;
			int len = (int) (entry.endPos() - entry.startPos());
			while (len > 0) {
				src.readFully(list, 0, Math.min(len, max));
				file.write(list, 0, max);
				len -= max;
			}
		}

		long delta = entryBeginOffset - entry.startPos();
		entry.offset += delta;
		ZipArchive.writeCEN(buf, entry);
		entry.offset -= delta;
		CENs.put(buf);
		eof.cDirTotal++;
		buf.clear();
	}

	private ZEntry entry;
	private long entryBeginOffset, dataBeginOffset;
	private boolean zip64;

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
		if (duplicate != null && !duplicate.add(ze.getName())) throw new ZipException("Duplicate entry " + ze.getName());
		entry = ze;
		// noinspection all
		if (ze.getMethod() == -1) ze.setMethod(ZipEntry.STORED);

		entryBeginOffset = file.position();

		byte[] extra = null;

		buf.putInt(HEADER_LOC)
		   .putShortLE(large?45:20)
		   .putShortLE(2048) // EFS
		   .putShortLE(ze.getMethod())
		   .putIntLE(ze.modTime)
		   .putIntLE(0) // crc32
		   .putIntLE(0) // csize
		   .putIntLE(0) // usize
		   .putShortLE(DynByteBuf.byteCountUTF8(ze.getName()))
		   .putShortLE((extra == null ? 0 : extra.length) + (large?20:0))
		   .putUTFData(ze.getName());

		zip64 = large;

		if (extra != null) buf.put(extra);
		if (large) buf.putShortLE(0x0001).putShortLE(16).putLongLE(0).putLongLE(0);

		file.write(buf);
		buf.clear();

		dataBeginOffset = file.position();
	}

	private static long getTime(ZipEntry ze) {
		long l = ze.getTime();
		if (l < 0) {
			l = System.currentTimeMillis();
			ze.setTime(l);
		}
		return l;
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
	public void write(@Nonnull byte[] b, int off, int len) throws IOException {
		if (entry == null) throw new ZipException("Entry closed");
		crc.update(b, off, len);
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
		if (large && !zip64)
			throw new ZipException("文件过大(4GB), 你要在beginEntry中打开zip64 (默认关闭以节约空间)");

		buf.putInt(HEADER_CEN)
		   .putShortLE(20)
		   .putShortLE(20)
		   .putShortLE(2048) // EFS
		   .putShortLE(entry.getMethod())
		   .putIntLE(entry.modTime)
		   .putIntLE((int) crc.getValue())
		   .putIntLE((int) (cSize >= U32_MAX ? U32_MAX : cSize))
		   .putIntLE((int) (uSize >= U32_MAX ? U32_MAX : uSize))
		   .putShortLE(DynByteBuf.byteCountUTF8(entry.getName()))
		   .putShortLE(0) // ext
		   .putShortLE(0) // comment
		   .putShortLE(0) // disk
		   .putShortLE(0) // attrIn
		   .putIntLE(0) // attrEx
		   .putIntLE((int) (entryBeginOffset >= U32_MAX ? U32_MAX : entryBeginOffset))
		   .putUTFData(entry.getName());

		if (large || entryBeginOffset >= U32_MAX) {
			buf.putShortLE(4, 45).putShortLE(6, 45);

			int begin = buf.wIndex();
			buf.putShortLE(1).putShortLE(0);

			int zx = 0;
			if (uSize >= U32_MAX) {
				buf.putLongLE(uSize);
				zx++;
			}
			if (cSize >= U32_MAX) {
				buf.putLongLE(uSize);
				zx++;
			}
			if (entryBeginOffset >= U32_MAX) {
				buf.putLongLE(entryBeginOffset);
				zx++;
			}

			buf.putShortLE(buf.wIndex()-(zx<<3), zx<<3)
			   .putShortLE(30, buf.wIndex()-begin);
		}
		CENs.put(buf);
		eof.cDirTotal++;

		f.seek(entryBeginOffset + 14);
		f.write(buf.list, 16, 12);
		buf.clear();

		if (large) {
			f.seek(dataBeginOffset-16);
			buf.clear();
			buf.putLongLE(uSize).putLongLE(cSize);
			f.write(buf);
		}

		f.seek(pos);

		crc.reset();
		entry = null;
	}

	public void finish() throws IOException {
		if (finish) return;

		if (entry != null) closeEntry();

		def.end();

		Source f = file;
		eof.cDirOffset = f.position();
		f.write(CENs);
		CENs.clear();

		eof.cDirLen = f.position()-eof.cDirOffset;
		ZipArchive.writeEND(buf, eof, f.position());
		f.write(buf.list, 0, buf.wIndex());

		if (f.length() != f.position()) // truncate
			f.setLength(f.position());
		f.close();

		finish = true;
	}

	@Override
	public void close() throws IOException {
		finish();
	}
}
