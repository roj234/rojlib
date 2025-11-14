package roj.archive.zip;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import roj.archive.ArchivePacker;
import roj.io.MBOutputStream;
import roj.io.source.CompositeSource;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

import static roj.archive.zip.ZipEditor.GP_HAS_EXT;
import static roj.archive.zip.ZipEditor.GP_UFS;
import static roj.archive.zip.ZipEntryWriter.*;

/**
 * @author Roj234
 * @since 2021/10/5
 * @revised 2025/12/28
 * @version 2.0
 */
public class ZipPacker extends MBOutputStream implements ArchivePacker<ZipFile, ZipEntry> {
	private final ByteList CENs = new ByteList(), buf = new ByteList();

	private final ZipEntryWriter impl;
	private byte[] password;

	private boolean finished;
	private int entryCount;
	private byte[] comment = ArrayCache.BYTES;

	public ZipPacker(File file) throws IOException { this(file, Deflater.DEFAULT_COMPRESSION, 0); }
	public ZipPacker(File file, int compression, long splitSize) throws IOException {
		this(splitSize != 0 ? CompositeSource.fixed(file, splitSize) : new FileSource(file), compression);
	}
	public ZipPacker(Source file, int compression) throws IOException {
		this.impl = new ZipEntryWriter(file, buf, compression);
	}

	public void setCompressionLevel(int compression) {impl.setCompressionLevel(compression);}

	public void setPassword(byte[] password) {this.password = password;}

	public void setComment(String comment) { this.comment = comment.getBytes(StandardCharsets.UTF_8); }
	public void setComment(byte[] comment) { this.comment = comment; }

	public void writeNamed(String name, DynByteBuf data) throws IOException { writeNamed(name, data, ZipEntry.DEFLATED); }
	public void writeNamed(String name, DynByteBuf data, int method) throws IOException { writeNamed(name, data, method, System.currentTimeMillis()); }
	public void writeNamed(
			String name, DynByteBuf data,
			@MagicConstant(intValues = {ZipEntry.DEFLATED, ZipEntry.STORED, ZipEntry.LZMA}) int method,
			long modTime
	) throws IOException {
		if (impl.entry != null) closeEntry();
		if (name.endsWith("/")) throw new ZipException("目录不是空的: "+name);

		var entry = new ZipEntry(name);
		entry.setMethod(method);
		entry.setModificationTime(modTime);
		entry.setGeneralPurposeFlags(GP_UFS);
		writeNamed(entry, data);
	}
	public void writeNamed(ZipEntry entry, DynByteBuf data) throws IOException {
		// 内存缓冲区的大小不可能超过2GB，更不用说4GB了
		beginEntry(entry, false, null);

		impl.write(data, true);
		//assert impl.compressedSize <= 0xFFFFFFFFL : "布什硌门，你怎么还能越压越大的？";
		closeEntry();
	}

	@Override
	public void copy(ZipFile owner, ZipEntry entry) throws IOException {
		if (impl.entry != null) closeEntry();

		var rawOut = impl.rawOut;

		long entryBeginOffset = rawOut.position();

		// 20250221 fixed 不知道长度的Entry经过openEntry已经知道了，所以需要更新LOC并移除EXT
		Source source = owner.openEntry(entry);
		try {
			if ((entry.flags & GP_HAS_EXT) != 0 && entry.getCompressedSize() != 0) {
				entry = entry.clone();
				entry.flags &= ~GP_HAS_EXT;
				writeLOC(rawOut, buf, entry);
				rawOut.put(source, entry.getOffset(), entry.getCompressedSize());
			} else {
				rawOut.put(source, entry.startPos(), entry.endPos() - entry.startPos());
			}
		} finally {
			owner.closeEntry(source);
		}

		long delta = entryBeginOffset - entry.startPos();
		entry.offset += delta;
		buf.clear();
		writeCEN(buf, entry);
		entry.offset -= delta;
		CENs.put(buf);
		entryCount++;
		buf.clear();
	}

	@Override
	public void beginEntry(ZipEntry entry) throws IOException {beginEntry(entry, false, null);}
	public void beginEntry(ZipEntry entry, boolean useZip64, byte[] password) throws IOException {
		if (impl.entry != null) closeEntry();

		if (password == null) password = this.password;
		impl.beginEntry(entry, true, useZip64, password);
	}

	@Override
	public void write(@NotNull byte[] b, int off, int len) throws IOException {impl.write(b, off, len);}

	@Override
	public void closeEntry() throws IOException {
		ZipEntry entry = impl.entry;
		if (entry == null) return;
		impl.closeEntry();

		writeCEN(CENs, entry);
		entryCount++;
	}

	@Override
	public void finish() throws IOException {
		synchronized (this) {
			if (finished) return;
			finished = true;
		}

		closeEntry();
		impl.finish();

		Source rawOut = impl.rawOut;

		long cenOffset = rawOut.position();
		long cenLength = CENs.readableBytes();

		rawOut.write(CENs);
		CENs.release();

		buf.clear();
		writeEND(buf, cenOffset, cenLength, entryCount, comment);
		rawOut.write(buf);
		buf.release();

		if (rawOut.length() != rawOut.position()) // truncate
			rawOut.setLength(rawOut.position());
		rawOut.close();
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			finish();
		} finally {
			impl.rawOut.close();
		}
	}
}