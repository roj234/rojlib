package roj.archive.zip;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveUtils;
import roj.archive.CRC32InputStream;
import roj.collect.SimpleList;
import roj.collect.XHashSet;
import roj.crypt.CipherInputStream;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.io.SourceInputStream;
import roj.io.source.BufferedSource;
import roj.io.source.Source;
import roj.reflect.ReflectionUtils;
import roj.util.ArrayCache;
import roj.util.ByteList;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/3/18 10:01
 */
public class ZipFile implements ArchiveFile {
	Source r;
	private Source fpRead;
	private static final long FPREAD_OFFSET = ReflectionUtils.fieldOffset(ZipFile.class, "fpRead");

	private static final XHashSet.Shape<String, ZEntry> ENTRY_SHAPE = XHashSet.shape(String.class, ZEntry.class, "name", "next");

	XHashSet<String, ZEntry> namedEntries;
	SimpleList<ZEntry> entries;

	private ByteList buf;
	final Charset cs;

	static final ThreadLocal<List<InflateIn>> INFS = ThreadLocal.withInitial(SimpleList::new);
	static final int MAX_INFS = 10;

	byte flags;

	public static final long U32_MAX = 4294967295L;
	public static final int ARRAY_READ_MAX = 100 << 20;

	static final int
		HEADER_EXT               = 0x504b0708,
		HEADER_ZIP64_END_LOCATOR = 0x504b0607,
		HEADER_ZIP64_END         = 0x504b0606,
		HEADER_END               = 0x504b0506,
		HEADER_LOC               = 0x504b0304,
		HEADER_CEN               = 0x504b0102;

	public static final int
		FLAG_KILL_EXT	   = 1,
		FLAG_VERIFY		   = 2,
		FLAG_BACKWARD_READ = 4,
		FLAG_FORCE_UTF     = 8;

	static final int
		GP_ENCRYPTED = 1,
		GP_HAS_EXT   = 8,
		GP_STRONG_ENC= 64,
		GP_UTF       = 2048,

		FLAG_HAS_ERROR = 128;

	public static final byte
		CRYPT_NONE = 0,
		CRYPT_ZIP2 = 1,
		CRYPT_AES  = 2,
		CRYPT_AES2 = 3;

	static final int
		VER_MZF = 54,
		ZIP_STORED = 10,
		ZIP_DEFLATED = 20,
		ZIP_64 = 45,
		ZIP_AES = 51;

	public ZipFile(String name) throws IOException { this(new File(name)); }
	public ZipFile(File file) throws IOException { this(file, FLAG_KILL_EXT|FLAG_BACKWARD_READ|FLAG_VERIFY); }
	public ZipFile(File file, int flag) throws IOException { this(file, flag, 0, StandardCharsets.UTF_8); }
	public ZipFile(File file, int flag, Charset charset) throws IOException { this(file, flag, 0, charset); }
	public ZipFile(File file, int flag, long offset, Charset charset) throws IOException {
		this.flags = (byte) flag;
		this.cs = charset;

		r = ArchiveUtils.tryOpenSplitArchive(file, true);
		r.seek(offset);
		if (r.length() > 0) reload();
	}

	public ZipFile(Source source, int flag, Charset cs) {
		this.r = source;
		this.flags = (byte) flag;
		this.cs = cs;
	}

	public final Source source() { return r; }
	public final boolean isClosed() { return r == null; }

	@Override
	public void close() throws IOException {
		Source r1 = r;
		if (r1 == null) return;
		r1.close();
		r = null;

		Source s = (Source) u.getAndSetObject(this, FPREAD_OFFSET, r1);
		if (s != null) s.close();
	}

	public final void reload() throws IOException {
		entries = new SimpleList<>();
		namedEntries = null;
		cDirLen = cDirOffset = 0;

		buf = new ByteList(256);
		try {
			if ((flags & FLAG_BACKWARD_READ) != 0 && r.hasChannel() && r.length() > 128) readBackward();
			else readForward();
		} catch (IOException e) {
			close();
			throw e;
		} finally {
			buf._free();
			buf = null;
		}
	}

	@Override
	public final ZEntry getEntry(String name) {
		if (namedEntries == null) {
			namedEntries = ENTRY_SHAPE.createSized(entries.size());
			for (int i = 0; i < entries.size(); i++) {
				ZEntry entry = entries.get(i);
				if (!namedEntries.add(entry)) {
					throw new IllegalArgumentException("文件名重复！该文件可能已损坏，请通过entries()获取Entry并读取");
				}
			}
			entries = null;
		}
		return namedEntries.get(name);
	}
	@Override
	public final Collection<ZEntry> entries() { return entries == null ? namedEntries : entries; }

	// region Load (LOC EXT CEN END)
	private void readForward() throws IOException {
		Source r1 = r;
		if (!r.isBuffered()) r = BufferedSource.wrap(r);

		SimpleList<ZEntry> locEntries = new SimpleList<>();

		// found_cen = 1
		// found_end = 2
		// found_zip64 = 4
		int state = 0;

		int field;

		try {
			loop:
			while (true) {
				field = r.asDataInput().readInt();
				switch (field) {
					case HEADER_LOC:
						if ((state&7) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readLOC(locEntries);
					break;
					case HEADER_CEN:
						if ((state&6) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readCEN(locEntries);
						state |= 1;
					break;
					case HEADER_END:
						if ((state&2) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readEND((state&4) != 0);
						state |= 2;
					break;
					case HEADER_ZIP64_END:
						if ((state&4) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readEND64();
						state |= 4;
					break;
					case HEADER_ZIP64_END_LOCATOR: r.skip(16); break;
					default: break loop;
				}

				if (r.position() >= r.length()) {
					if (r != r1) r.close();
					r = r1;

					if ((state&1) == 0 ||
						(state&6) == 0 ||
						(flags&FLAG_HAS_ERROR) != 0 ||
						locEntries.size() != entries.size()) {

						flags |= FLAG_HAS_ERROR;
						entries = locEntries;
					}

					return;
				}
			}

			throw new ZipException("未知的ZIP头: 0x"+Integer.toHexString(field)+", pos="+r.position());
		} catch (Throwable e) {
			if (r != r1) r.close();
			r = r1;

			throw e;
		}
	}
	private void readBackward() throws IOException {
		long off = Math.max(r.length()-1024, 0);

		MappedByteBuffer mb = r.channel().map(MapMode.READ_ONLY, off, r.length()-off);
		mb.order(ByteOrder.BIG_ENDIAN);

		boolean hasEnd = false;
		int pos = mb.capacity()-3;
		while (pos > 0) {
			if ((mb.get(--pos) & 0xFF) != 'P') continue;

			int field = mb.getInt(pos);
			if (field == HEADER_END) {
				r.seek(off+pos+4);

				if (!readEND(false)) break;

				hasEnd = true;
			} else if (field == HEADER_ZIP64_END) {
				r.seek(off+pos+4);

				readEND64();

				if (hasEnd) break;
			}
		}

		NIOUtil.clean(mb);
		if (pos == 0) {
			flags &= ~FLAG_BACKWARD_READ;
			r.seek(0);
			readForward();
		} else {
			entries.ensureCapacity(cDirOnDisk);

			Source r1 = r;
			if (!r.isBuffered()) r = BufferedSource.wrap(r);

			try {
				r.seek(cDirOffset);
				while (r.position() < r.length()) {
					int header = r.asDataInput().readInt();
					switch (header) {
						/*ByteList buf = read(16);

						if (!zip64) {
							System.out.println("HEADER_ZIP64_END_LOCATOR is not fully implemented!");
							r.seek(buf.readLongLE(4));
						}*/
						// 0  u4 eof_disk
						// 4  u8 position
						// 12 u4 total_disk
						case HEADER_ZIP64_END_LOCATOR: r.skip(16); break;
						case HEADER_ZIP64_END: case HEADER_END: return;
						case HEADER_CEN: readCEN(null); break;
						default: throw new ZipException("未知的ZIP头: 0x"+Integer.toHexString(header));
					}
				}
			} finally {
				if (r1 != r) {
					r.close();
					r = r1;
				}
			}
		}
	}

	private ByteList read(int len) throws IOException {
		ByteList b = buf; b.clear();
		b.ensureCapacity(len);
		r.readFully(b.list, 0, len);
		b.wIndex(len);
		return b;
	}
	private void readLOC(SimpleList<ZEntry> locEntries) throws IOException {
		ByteList buf = read(26);
		ZEntry entry = new ZEntry();

		//entry.minExtractVer = buffer.readUShortLE(0);
		int flags = buf.readUShortLE(2);
		entry.flags = (char) flags;
		entry.method = (char) buf.readUShortLE(4);
		entry.modTime = buf.readIntLE(6);
		entry.crc32 = buf.readIntLE(10);
		long cSize = buf.readUIntLE(14);
		entry.cSize = cSize;
		entry.uSize = buf.readUIntLE(18);

		readName(entry, flags, buf.readUShortLE(22));
		int extraLen = buf.readUShortLE(24);

		if (extraLen > 0) {
			buf = read(extraLen);
			entry.extraLenOfLOC = (char) extraLen;
			entry.readLOCExtra(this, buf);
		}

		long off = r.position();
		if (off > r.length()) throw new EOFException();
		entry.offset = off;

		// obviously not support encrypted files
		// 8; HAS EXT
		if ((flags & 8) != 0 && cSize == 0) {
			// skip method
			InflateIn in1 = (InflateIn) getCachedInflater(r.asInputStream());
			byte[] tmp = buf.list;
			while (in1.read(tmp) >= 0);

			Inflater inf = in1.getInf();

			// 不删除EXT标记，只是保存大小，这样不需要【移动文件】这么大的IO
			if ((this.flags & FLAG_KILL_EXT) != 0) {
				if (inf.getBytesRead() < U32_MAX && inf.getBytesWritten() < U32_MAX) {
					// top + header + offset (usize)
					r.seek(entry.startPos() + 4 + 14);
					// C(ompressed)Size
					r.writeInt(Integer.reverseBytes(inf.getTotalIn()));
					// U(ncompressed)Size
					r.writeInt(Integer.reverseBytes(inf.getTotalOut()));
				}
			}

			entry.cSize = inf.getBytesRead();
			entry.uSize = inf.getBytesWritten();

			r.seek(off + inf.getBytesRead());
			skipEXT(entry);

			in1.close();
		} else {
			r.seek(off + cSize);
			if ((flags & 8) != 0) skipEXT(entry);
		}
		entry.setEndPos(r.position());

		locEntries.add(entry);
	}
	private void skipEXT(ZEntry entry) throws IOException {
		boolean is64 = entry.cSize >= U32_MAX | entry.uSize >= U32_MAX;
		if (r.asDataInput().readInt() != HEADER_EXT) {
			r.skip(is64 ? 16 : 8);
		} else {
			r.skip(is64 ? 20 : 12);
		}
	}
	private void readName(ZEntry entry, int flags, int nameLen) throws IOException {
		entry.nameBytes = new byte[nameLen];
		r.readFully(entry.nameBytes);
		if (cs == StandardCharsets.UTF_8 || (flags & GP_UTF) != 0) {
			entry.name = IOUtil.SharedCoder.get().decode(entry.nameBytes);
		} else {
			entry.name = new String(entry.nameBytes, 0, nameLen, cs);
		}
	}
	private void readCEN(SimpleList<ZEntry> entryForward) throws IOException {
		ByteList buf = read(42);
		ZEntry entry = new ZEntry();

		//entry.ver = buf[0] | buf[1] << 8;
		//entry.minExtractVer = buf[2] | buf[3] << 8;
		entry.flags = (char) buf.readUShortLE(4);
		entry.method = (char) buf.readUShortLE(6);
		entry.modTime = buf.readIntLE(8);
		entry.crc32 = buf.readIntLE(12);
		entry.cSize = buf.readUIntLE(16);
		entry.uSize = buf.readUIntLE(20);

		//entry.disk = (char) buf.readUShortLE(30);
		entry.internalAttr = (char) buf.readUShortLE(32);
		entry.externalAttr = buf.readIntLE(34);
		long fileHeader = buf.readUIntLE(38);

		int nameLen = buf.readUShortLE(24);

		readName(entry, entry.flags, nameLen);

		// ignore per-file comment
		int commentLen = buf.readUShortLE(28);
		r.skip(commentLen);

		int extraLen = buf.readUShortLE(26);
		if (extraLen > 0) {
			buf = read(extraLen);
			fileHeader = entry.readCENExtra(this, buf, fileHeader);
		}

		entry.mzFlag |= ZEntry.MZ_BACKWARD;
		entry.offset = fileHeader + 30 + nameLen;

		if (entryForward == null) {
			if (!entries.isEmpty() && entries.getLast().offset > entry.offset) {
				flags |= FLAG_HAS_ERROR;
				entry.mzFlag |= ZEntry.MZ_ERROR;
			}
		} else {
			if (entries.size() >= entryForward.size()) {
				flags |= FLAG_HAS_ERROR;
				entry.mzFlag |= ZEntry.MZ_ERROR;
			} else {
				ZEntry prev = entryForward.get(entries.size());
				if (!prev.merge(entry)) {
					if ((flags&FLAG_VERIFY) != 0)
						throw new ZipException("压缩参数在LOC和CEN间不匹配("+prev+", "+entry+")");
					flags |= FLAG_HAS_ERROR;
					entry.mzFlag |= ZEntry.MZ_ERROR;
				} else {
					entry = prev;
				}
			}
		}

		entries.add(entry);
	}
	private boolean readEND(boolean zip64) throws IOException {
		ByteList buf = read(18);

		if (!zip64) {
			//diskId = buf.readUShortLE(0);
			//cDirBegin = buf.readUShortLE(2);
			cDirOnDisk = buf.readUShortLE(4);
			cDirTotal = buf.readUShortLE(6);

			cDirLen = buf.readUIntLE(8);
			cDirOffset = buf.readUIntLE(12);
		}

		int commentLen = buf.readUShortLE(16);
		if (commentLen > 0) {
			buf = read(commentLen);
			comment = buf.toByteArray();
		} else {
			comment = ArrayCache.BYTES;
		}

		return !zip64 && (cDirLen == U32_MAX || cDirOffset == U32_MAX || cDirOnDisk == 0xFFFF);
	}
	private void readEND64() throws IOException {
		ByteList buf = read((int) read(8).readLongLE());
		// 0  u2 ver
		// 2  u2 ver
		// 4  u4 diskId
		// 8  u4 attrBeginId ???啥意思
		// 12 u8 diskEntryCount
		// 20 u8 totalEntryCount
		// 28 u8 cDirLen
		// 36 u8 cDirBegin
		cDirOnDisk = (int) buf.readLongLE(12);
		cDirTotal = (int) buf.readLongLE(20);

		cDirLen = buf.readLongLE(28);
		cDirOffset = buf.readLongLE(36);
	}

	private void initDataOffset(Source r, ZEntry entry) throws IOException {
		if ((entry.mzFlag & ZEntry.MZ_ERROR) != 0 && (flags & FLAG_VERIFY) != 0) throw new ZipException(entry+"报告自身已损坏");
		if ((entry.mzFlag & ZEntry.MZ_BACKWARD) == 0) return;

		r.seek(entry.startPos() + 28);

		int extraLen = r.read() | (r.read()<<8);
		if (extraLen < 0) throw new EOFException();

		entry.mzFlag ^= ZEntry.MZ_BACKWARD;
		entry.extraLenOfLOC = (char) extraLen;
		entry.offset += extraLen;
	}
	// endregion

	int cDirOnDisk, cDirTotal;
	long cDirLen, cDirOffset;
	byte[] comment = ArrayCache.BYTES;

	public final byte[] getComment() { return comment; }
	public final String getCommentString() { return new String(comment, cs); }

	@Override
	public final String toString() { return "ZipArchive{" + "files=" + cDirTotal + ", comment='" + getCommentString() + '\'' + '}'; }

	// region Read
	public final InputStream getFileStream(ZEntry entry) throws IOException {
		if (entry.nameBytes == null) throw new ZipException("ZEntry不是从文件读取的");

		Source src = (Source) u.getAndSetObject(this, FPREAD_OFFSET, null);
		if (src == null) src = r.threadSafeCopy();

		initDataOffset(src, entry);
		src.seek(entry.offset);
		return new SourceInputStream.Shared(src, entry.cSize, this, FPREAD_OFFSET);
	}

	public final byte[] get(String entry) throws IOException {
		ZEntry file = getEntry(entry);
		if (file == null) return null;
		return get(file);
	}
	public final byte[] get(ZEntry file) throws IOException {
		if (file.uSize > ARRAY_READ_MAX) throw new ZipException("Entry too large, either use a pre-sized array or use streaming method");
		ByteList buf1 = ByteList.wrapWrite(new byte[(int) file.uSize], 0, (int) file.uSize);
		return get(file, buf1).list;
	}
	public ByteList get(ZEntry file, ByteList buf) throws IOException {
		buf.ensureCapacity((int) (buf.wIndex() + file.uSize));
		return buf.readStreamFully(getStream(file, null));
	}

	public final InputStream getStream(String name) throws IOException {
		ZEntry entry = getEntry(name);
		if (entry == null) return null;
		return getStream(entry, null);
	}
	public final InputStream getStream(ZEntry entry) throws IOException { return getStream(entry, null); }
	public final InputStream getStream(ArchiveEntry entry, byte[] pw) throws IOException { return getStream((ZEntry) entry, pw); }
	public InputStream getStream(ZEntry entry, byte[] pw) throws IOException {
		InputStream in = getFileStream(entry);

		if (entry.isEncrypted()) {
			if (pw == null) throw new IllegalArgumentException("缺少密码: "+entry);
			if (entry.getEncryptType() == CRYPT_ZIP2) {
				ZipCrypto c = new ZipCrypto();
				c.init(ZipCrypto.DECRYPT_MODE, pw);

				in = new CipherInputStream(in, c);

				// has ext, CRC cannot be computed before
				int checkByte = (entry.flags & GP_HAS_EXT) != 0 ? 0xFF&(entry.modTime >>> 8) : entry.crc32 >>> 24;

				long r = in.skip(11);
				if (r < 11) throw new ZipException("数据错误: "+r);

				int myCb = in.read();
				if (myCb != checkByte && (flags & FLAG_VERIFY) != 0) throw new ZipException("校验错误: check="+checkByte+", read="+myCb);
			} else {
				ZipAES c = new ZipAES();
				boolean checkPassed = c.setKeyDecrypt(pw, in);
				if (!checkPassed && (flags & FLAG_VERIFY) != 0) throw new ZipException("校验错误: 密码错误？");

				((SourceInputStream) in).remain -= 10;
				if ((flags & FLAG_VERIFY) != 0) {
					in = new AESInputSt(in, c);
				} else {
					in = new CipherInputStream(in, c);
				}
			}
		}

		if (entry.method == ZipEntry.DEFLATED) in = getCachedInflater(in);

		if ((entry.mzFlag & ZEntry.MZ_NoCrc) == 0 && (flags & FLAG_VERIFY) != 0)
			in = new CRC32InputStream(in, entry.crc32);

		return in;
	}
	// endregion

	public static InputStream getCachedInflater(InputStream in) {
		List<InflateIn> infs = INFS.get();
		if (infs.isEmpty()) in = new InflateIn(in);
		else in = infs.remove(infs.size() - 1).reset(in);
		return in;
	}
}