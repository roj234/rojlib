package roj.archive.zip;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ChecksumInputStream;
import roj.archive.SourceStreamCAS;
import roj.collect.*;
import roj.collect.RSegmentTree.Range;
import roj.crypt.CipherInputStream;
import roj.crypt.CipherOutputStream;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.io.SafeInputStream;
import roj.io.SourceInputStream;
import roj.io.source.BufferedSource;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.io.source.SplittedSource;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;
import java.util.zip.*;

import static roj.archive.zip.ZEntry.MZ_HASCEN;
import static roj.archive.zip.ZEntry.MZ_NOCRC;
import static roj.reflect.FieldAccessor.u;

/**
 * 支持分卷压缩文件
 * 支持AES、ZipCrypto加密的读写
 * 支持任意编码，支持InfoZip的UTF文件名
 * 支持ZIP64
 *
 * @author Roj233
 * @since 2021/7/10 17:09
 */
public class ZipArchive implements ArchiveFile {
	public File file;

	private Source r;
	private Source fpRead;
	private static long FPREAD_OFFSET;
	static {
		try {
			FPREAD_OFFSET = u.objectFieldOffset(ZipArchive.class.getDeclaredField("fpRead"));
		} catch (NoSuchFieldException ignored) {}
	}

	private final MyHashMap<String, ZEntry> entries;
	private final MyHashSet<EntryMod> modified;

	private final END end;

	private final ByteList buf;
	private final Charset cs;

	private CRC32 crc;
	private Inflater inflater;
	private Deflater deflater;

	private static final Comparator<ZEntry> CEN_SORTER = (o1, o2) -> Long.compare(o1.offset, o2.offset);
	static final ThreadLocal<List<InflateIn>> inflaters = ThreadLocal.withInitial(SimpleList::new);
	static final int MAX_INFLATER_SIZE = 10;

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
		FLAG_FORCE_UTF     = 16,
		FLAG_LINKED_MAP    = 32;

	static final int
		GP_ENCRYPTED = 1,
		GP_HAS_EXT   = 8,
		GP_STRONG_ENC= 64,
		GP_UTF       = 2048,
		GP_LOC_ENC   = 1<<13;

	public static final int
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

	public ZipArchive(String name) throws IOException {
		this(new File(name), FLAG_KILL_EXT|FLAG_BACKWARD_READ, 0, StandardCharsets.UTF_8);
	}

	public ZipArchive(File file) throws IOException {
		this(file, FLAG_KILL_EXT|FLAG_BACKWARD_READ, 0, StandardCharsets.UTF_8);
	}

	public ZipArchive(File file, int flag) throws IOException {
		this(file, flag, 0, StandardCharsets.UTF_8);
	}

	public ZipArchive(File file, int flag, Charset charset) throws IOException {
		this(file, flag, 0, charset);
	}

	public ZipArchive(File file, int flag, long offset, Charset charset) throws IOException {
		this(flag, charset);
		this.file = file;
		r = new FileSource(file);
		if (file.getName().endsWith(".001")) {
			r = BufferedSource.autoClose(new SplittedSource((FileSource) r, file.length()));
		}
		r.seek(offset);

		if (r.length() > 0) reload();
	}

	public ZipArchive(Source source, int flag, Charset cs) {
		this(flag, cs);
		file = null;
		r = source;
	}

	private ZipArchive(int flag, Charset cs) {
		entries = ((flag&FLAG_LINKED_MAP) != 0) ? new LinkedMyHashMap<>() : new MyHashMap<>();
		modified = new MyHashSet<>();
		end = new END();
		buf = new ByteList(1024);
		flags = (byte) flag;
		this.cs = cs;
	}

	// region File

	public Source getFile() {
		return r;
	}

	public final boolean isEmpty() {
		return entries.isEmpty();
	}
	public final boolean isOpen() {
		return r != null;
	}

	/**
	 * re-open internal RandomAccessFile, to continue operate
	 */
	public void reopen() throws IOException {
		if (file == null) throw new IOException("Source is not file");
		if (buf.list == null) throw new IOException("fully closed");
		if (r == null) r = new FileSource(file);
		if ((flags & (FLAG_VERIFY)) != 0) verify();
	}

	/**
	 * closes associated RandomAccessFile, so the zip file can be modified by external programs
	 */
	public void closeFile() throws IOException {
		if (r != null) {
			r.close();
			r = null;
		}

		Source s;
		do {
			s = fpRead;
		} while (!u.compareAndSwapObject(this, FPREAD_OFFSET, s, null));
		if (s != null) s.close();
	}

	public final boolean isClosed() {
		return buf.list == null;
	}

	@Override
	public void close() throws IOException {
		buf.list = null;

		if (deflater != null) {
			deflater.end();
			deflater = null;
		}
		modified.clear();
		closeFile();
	}

	public void empty() throws IOException {
		entries.clear();
		modified.clear();
		end.cDirLen = end.cDirOffset = 0;
		end.cDirTotal = 0;

		r.setLength(0);
		buf.clear();
		writeEND(buf, end, 0);
		r.write(buf);
	}

	// endregion
	// region Load

	public final void reload() throws IOException {
		entries.clear();
		modified.clear();
		end.cDirLen = end.cDirOffset = 0;

		try {
			if ((flags & FLAG_BACKWARD_READ) != 0 && r.hasChannel() && r.length() > 128) readBackward();
			else readForward();
		} catch (EOFException e) {
			ZipException ze = (ZipException) new ZipException("Unexpected EOF at " + r.position()).initCause(e);
			closeFile();
			throw ze;
		} catch (IOException e) {
			closeFile();
			throw e;
		}

		if ((flags & (FLAG_VERIFY)) != 0) verify();
	}

	public final void verify() throws IOException {
		for (ZEntry file : entries.values()) {
			if ((file.mzfFlag & MZ_HASCEN) == 0) throw new ZipException(file.name + " missing CEN!");
		}
		if (end.cDirLen != 0) {
			r.seek(end.cDirOffset);
			int v = r.asDataInput().readInt();
			if (v != HEADER_CEN) {
				throw new ZipException("CEN offset: exc " + Integer.toHexString(HEADER_CEN) + " got " + Integer.toHexString(v) + " at " + r.position());
			}
		}
	}

	private void readForward() throws IOException {
		Source r1 = r;
		if (!r.isBuffered()) r = BufferedSource.wrap(r);

		// found_end = 1
		// found_zip64 = 2
		int state = 0;

		int field;

		try {
			loop:
			while (true) {
				field = r.asDataInput().readInt();
				switch (field) {
					case HEADER_END:
						if ((state&1) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readEND((state&2) != 0);
						state |= 1;
						break;
					case HEADER_ZIP64_END:
						if ((state&2) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readEND64();
						state |= 2;
						break;
					case HEADER_ZIP64_END_LOCATOR: r.skip(16); break;
					case HEADER_CEN:
						if ((state&3) != 0 && (flags & FLAG_VERIFY) != 0) break loop;
						if ((flags & FLAG_BACKWARD_READ) != 0) return;

						readCEN();
						break;
					case HEADER_LOC:
						if ((state&3) != 0 && (flags & FLAG_VERIFY) != 0) break loop;

						readLOC();
						break;
					default: break loop;
				}

				if (r.position() >= r.length()) {
					if (r != r1) r.close();
					r = r1;
					return;
				}
			}

			throw new ZipException("未预料的ZIP头: " + Integer.toHexString(field) + " at " + r.position());
		} catch (Throwable e) {
			if (r != r1) r.close();
			r = r1;

			throw e;
		}
	}
	private void readBackward() throws IOException {
		// 65610 => 0xFFFF(comment) + 18(EOF) + 56(Zip64) + 1(pos != 0)
		long off = Math.max(r.length()-65611, 0);

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
			entries.ensureCapacity(end.cDirOnDisk);

			Source r1 = r;
			if (!r.isBuffered()) r = BufferedSource.wrap(r);

			try {
				r.seek(end.cDirOffset);
				while (r.position() < r.length()) {
					int header = r.asDataInput().readInt();
					switch (header) {
						/*ByteList buf = read(16);

						if (!end.zip64) {
							System.out.println("HEADER_ZIP64_END_LOCATOR is not fully implemented!");
							r.seek(buf.readLongLE(4));
						}*/
						// 0  u4 eof_disk
						// 4  u8 position
						// 12 u4 total_disk
						case HEADER_ZIP64_END_LOCATOR: r.skip(16); break;
						case HEADER_ZIP64_END: case HEADER_END: return;
						case HEADER_CEN: readCEN(); break;
						default: throw new ZipException("Unexpected ZIP Header: " + Integer.toHexString(header));
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

	private void readLOC() throws IOException {
		ByteList buf = read(26);
		ZEntry entry = new ZEntry(true);

		//entry.minExtractVer = buffer.readUShortLE(0);
		int flags = buf.readUShortLE(2);
		entry.flags = (char) flags;
		int cp = entry.method = (char) buf.readUShortLE(4);
		entry.modTime = buf.readIntLE(6);
		entry.CRC32 = buf.readIntLE(10);
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
			Inflater inf = inflater;
			if (inf == null) inf = inflater = new Inflater(true);
			byte[] tmp = buf.list;
			try {
				while (true) {
					if (inf.inflate(tmp, 512, 512) == 0) {
						if (inf.finished() || inf.needsDictionary()) {
							// not update EXT flag: not more I/O (moving)
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
							break;
						}
						if (inf.needsInput()) {
							int read = r.read(tmp, 0, 512);
							if (read <= 0) throw new EOFException("Before entry decompression completed");

							inf.setInput(tmp, 0, read);
						}
					}
				}
			} catch (DataFormatException e) {
				ZipException err = new ZipException("Data format: " + e.getMessage());
				err.initCause(e);
				throw err;
			} finally {
				inf.reset();
			}
		} else {
			r.seek(off + cSize);
			if ((flags & 8) != 0) skipEXT(entry);
		}
		entry.setEndPos(r.position());

		ZEntry prev = entries.putIfAbsent(entry.name, entry);
		if (prev != null) {
			prev.merge(entries, entry);
		}
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
	private void readCEN() throws IOException {
		ByteList buf = read(42);
		ZEntry entry = new ZEntry(false);

		//entry.ver = buf[0] | buf[1] << 8;
		//entry.minExtractVer = buf[2] | buf[3] << 8;
		entry.flags = (char) buf.readUShortLE(4);
		entry.method = (char) buf.readUShortLE(6);
		entry.modTime = buf.readIntLE(8);
		entry.CRC32 = buf.readIntLE(12);
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

		long off = r.position();

		if ((flags & FLAG_BACKWARD_READ) != 0) {
			r.seek(fileHeader + 28);
			extraLen = r.read() | (r.read()<<8);
			if (extraLen < 0) throw new EOFException();

			entry.offset = fileHeader + 30 + nameLen + extraLen;
			entry.extraLenOfLOC = (char) extraLen;
			entries.put(entry.name, entry);

			r.seek(off);
		} else {
			entry.offset = -1;

			ZEntry prev = entries.putIfAbsent(entry.name, entry);
			if (prev != null) {
				if (prev.merge(entries, entry)) {
					entry = prev;
				}
			}
		}

		if (fileHeader != entry.startPos()) {
			throw new ZipException(entry.name + " offset mismatch: req " + fileHeader + " computed " + entry.startPos());
		}

		if (off > r.length()) throw new EOFException();
	}
	private boolean readEND(boolean zip64) throws IOException {
		ByteList buf = read(18);
		END end = this.end;

		if (!zip64) {
			//end.diskId = buf.readUShortLE(0);
			//end.cDirBegin = buf.readUShortLE(2);
			end.cDirOnDisk = buf.readUShortLE(4);
			end.cDirTotal = buf.readUShortLE(6);

			end.cDirLen = buf.readUIntLE(8);
			end.cDirOffset = buf.readUIntLE(12);
		}

		int commentLen = buf.readUShortLE(16);
		if (commentLen > 0) {
			buf = read(commentLen);
			end.comment = buf.toByteArray();
		} else {
			end.comment = ArrayCache.BYTES;
		}

		return !zip64 && (end.cDirLen == U32_MAX || end.cDirOffset == U32_MAX || end.cDirOnDisk == 0xFFFF);
	}
	private void readEND64() throws IOException {
		ByteList buf = read((int) read(8).readLongLE());
		END end = this.end;
		// 0  u2 ver
		// 2  u2 ver
		// 4  u4 diskId
		// 8  u4 attrBeginId ???啥意思
		// 12 u8 diskEntryCount
		// 20 u8 totalEntryCount
		// 28 u8 cDirLen
		// 36 u8 cDirBegin
		end.cDirOnDisk = (int) buf.readLongLE(12);
		end.cDirTotal = (int) buf.readLongLE(20);

		end.cDirLen = buf.readLongLE(28);
		end.cDirOffset = buf.readLongLE(36);
	}

	// endregion

	public MyHashMap<String, ZEntry> getEntries() {
		return entries;
	}

	public InputStream getInputStream(ArchiveEntry entry, byte[] password) throws IOException {
		return getStream((ZEntry) entry, password);
	}

	public END getEND() {
		return end;
	}

	public InputStream i_getRawData(ZEntry entry) throws IOException {
		Source src;
		do {
			src = fpRead;
			if (src == null) {
				src = r.threadSafeCopy();
				src = src.isBuffered()?src:BufferedSource.autoClose(src);
				break;
			}
		} while (!u.compareAndSwapObject(this, FPREAD_OFFSET, src, null));

		src.seek(entry.offset);
		return new SourceStreamCAS(src, entry.cSize, this, FPREAD_OFFSET);
	}

	// region Read

	public byte[] get(String entry) throws IOException {
		ZEntry file = entries.get(entry);
		if (file == null) return null;
		return get(file);
	}
	public byte[] get(ZEntry file) throws IOException {
		if (file.uSize > ARRAY_READ_MAX) throw new ZipException("Entry too large, either use a pre-sized array or use streaming method");
		return get(file, new ByteList((int) file.uSize)).list;
	}
	public ByteList get(ZEntry file, ByteList buf) throws IOException {
		buf.ensureCapacity((int) (buf.wIndex() + file.uSize));
		return buf.readStreamFully(getStream(file, null));
	}

	public InputStream getStream(String entry) throws IOException {
		ZEntry file = entries.get(entry);
		if (file == null) return null;
		return getStream(file, null);
	}
	public InputStream getStream(ZEntry file) throws IOException {
		return getStream(file, null);
	}
	public InputStream getStream(ZEntry file, byte[] pass) throws IOException {
		InputStream in = i_getRawData(file);

		if (file.isEncrypted()) {
			if (pass == null) throw new IOException("File is encrypted: " + file);
			if (file.getEncryptType() == CRYPT_ZIP2) {
				ZipCrypto c = new ZipCrypto();
				c.init(ZipCrypto.DECRYPT_MODE, pass);

				in = new CipherInputStream(in, c);

				// has ext, CRC cannot be computed before
				int checkByte = (file.flags & GP_HAS_EXT) != 0 ? 0xFF&(file.modTime >>> 8) : file.CRC32 >>> 24;
				if (in.skip(11) < 11) throw new IOException("Early EOF");

				int myCb = in.read();
				if (myCb != checkByte && (flags & FLAG_VERIFY) != 0) throw new ZipException("Checksum error: except " + checkByte + " got " + myCb);
			} else {
				ZipAES c = new ZipAES();
				boolean checkPassed = c.setKeyDecrypt(pass, in);
				if (!checkPassed && (flags & FLAG_VERIFY) != 0) throw new ZipException("Checksum error: wrong password ?");

				((SourceInputStream) in).remain -= 10;
				if ((flags & FLAG_VERIFY) != 0) {
					in = new AESInputSt(in, c);
				} else {
					in = new CipherInputStream(in, c);
				}
			}
		}

		if (file.method == ZipEntry.DEFLATED) in = _cachedInflate(in);

		if ((file.mzfFlag & MZ_NOCRC) == 0 && (flags & FLAG_VERIFY) != 0)
			in = new ChecksumInputStream(in, new CRC32(), file.CRC32 & 0xFFFFFFFFL);

		return in;
	}

	public static InputStream _cachedInflate(InputStream in) {
		List<InflateIn> infs = inflaters.get();
		if (infs.isEmpty()) in = new InflateIn(in);
		else in = infs.remove(infs.size() - 1).reset(in);
		return in;
	}

	// endregion

	// content == null : 删除
	public EntryMod put(String entry, ByteList content) {
		EntryMod file = put0(entry);
		file.flag = (byte) (content != null && content.readableBytes() > 100 ? 8 : 0);
		file.data = content;
		return file;
	}
	public EntryMod put(String entry, Supplier<ByteList> content, boolean compress) {
		EntryMod file = put0(entry);
		file.flag = (byte) (compress ? 8 : 0);
		file.data = content;
		return file;
	}
	public EntryMod putStream(String entry, InputStream content, boolean compress) {
		EntryMod file = put0(entry);
		file.flag = (byte) (compress ? 8 : 0);
		file.data = content;
		return file;
	}
	public EntryMod put0(String entry) {
		EntryMod file = new EntryMod();
		file.name = entry;
		if (file == (file = modified.find(file))) {
			file.entry = entries.get(entry);
			modified.add(file);
		}
		return file;
	}

	public void putAll(Map<String, ByteList> map) {
		EntryMod file = new EntryMod();

		for (Map.Entry<String, ByteList> entry : map.entrySet()) {
			file.name = entry.getKey();

			ByteList bl = entry.getValue();
			file.flag = (byte) (bl != null && bl.readableBytes() > 127 ? 8 : 0);
			file.data = bl;

			ZEntry attr = file.entry = entries.get(file.name);
			if (attr != null) {
				if (bl.readableBytes() == attr.uSize) {
					crc.reset();
					crc.update(bl.list, bl.arrayOffset()+bl.rIndex, bl.readableBytes());
					if ((int) crc.getValue() == attr.CRC32) {
						// apparently same file, skip
						continue;
					}
				}
			}

			if (file == modified.intern(file)) {
				file = new EntryMod();
			}
		}
	}

	public MyHashSet<EntryMod> getModified() {
		return modified;
	}

	public void store() throws IOException {
		store(Deflater.DEFAULT_COMPRESSION);
	}
	public void store(int level) throws IOException {
		if (modified.isEmpty()) return;
		//if ((flags & FLAG_READ_ATTR) != 0) throw new ZipException("This MutableZipFile is read-only!");
		if (deflater == null) {
			deflater = new Deflater(level, true);
			crc = new CRC32();
		} else {
			deflater.setLevel(level);
		}

		ZEntry minFile = null;

		RSegmentTree<ZEntry> uFile = new RSegmentTree<>((int) Math.log(entries.size()), false, modified.size());

		for (Iterator<EntryMod> itr = modified.iterator(); itr.hasNext(); ) {
			EntryMod file = itr.next();
			checkName(file);

			ZEntry o = entries.get(file.name);
			if (o != null) {
				if (minFile == null || minFile.offset > o.offset) {
					minFile = o;
				}

				if (file.data == null) {
					entries.remove(file.name);
					// 删除【删除】类型的EntryMod
					itr.remove();
				} else uFile.add(o);
			} else if (file.data == null) {
				// not found, 多半是entries被外部修改了
				itr.remove();
			}

			file.entry = o;
		}

		// write linear EFile header
		if (minFile != null) {
			for (ZEntry file : entries.values()) {
				if (file.offset >= minFile.offset && !uFile.add(file)) { // ^=
					uFile.remove(file);
				}
			}

			r.seek(minFile.startPos());

			ZEntry finalMinFile = minFile;
			uFile.mergeConnected(new ObjLongConsumer<List<? extends Range>>() {
				long delta, prevEnd = finalMinFile.startPos();

				@Override
				public void accept(List<? extends Range> files, long length) {
					ZEntry file1 = (ZEntry) files.get(0);
					long begin = file1.startPos();

					// delta一定小于0
					delta += prevEnd - begin;
					prevEnd = begin + length;

					for (int i = 0; i < files.size(); i++) {
						file1 = (ZEntry) files.get(i);
						file1.offset += delta;
					}

					try {
						r.moveSelf(begin, r.position(), length);
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}
			});
		} else {
			r.seek(end.cDirOffset);
		}

		OutputStream out = r;

		ByteList bw = buf; bw.clear();
		bw.ensureCapacity(2048);

		long precisionModTime = System.currentTimeMillis();
		int modTime = ZEntry.java2DosTime(precisionModTime);

		// write LOCs
		for (Iterator<EntryMod> itr = modified.iterator(); itr.hasNext(); ) {
			EntryMod mf = itr.next();
			if (mf.data == null) {
				itr.remove();
				continue;
			}

			ZEntry e = mf.entry;
			if (e == null) {
				e = mf.entry = new ZEntry((Boolean) null);
				entries.put(e.name = mf.name, e);
				if ((flags & FLAG_FORCE_UTF) != 0 || (mf.flag & EntryMod.E_UTF_NAME) != 0) {
					e.flags = GP_UTF;
					e.nameBytes = IOUtil.SharedCoder.get().encode(mf.name);
				} else {
					e.nameBytes = mf.name.getBytes(cs);
				}
			} else if ((mf.flag & EntryMod.E_UTF_NAME) != 0 && (e.flags & GP_UTF) == 0) {
				e.flags |= GP_UTF;
				e.nameBytes = IOUtil.SharedCoder.get().encode(e.name);
			}

			e.prepareWrite(mf.cryptType);
			e.offset = r.position() + 30 + e.nameBytes.length;
			e.method = (char) (mf.flag & EntryMod.E_COMPRESS);
			if ((mf.flag & EntryMod.E_ORIGINAL_TIME) == 0) {
				e.precisionModTime = precisionModTime;
				e.modTime = modTime;
			}

			// prepare
			crc.reset();

			if (mf.data instanceof Supplier) mf.data = ((Supplier<?>) mf.data).get();

			if (!(mf.data instanceof InputStream)) {
				ByteList data = (ByteList) mf.data;

				crc.update(data.list, data.arrayOffset() + data.rIndex, data.readableBytes());
				e.CRC32 = (int) crc.getValue();

				e.uSize = data.readableBytes();
				if (e.method == 0) e.cSize = e.uSize;

				long offset = r.position();
				writeLOC(out, bw, e);

				ZipAES za = null;
				OutputStream cout = out;
				if (mf.cryptType != CRYPT_NONE) {
					if (mf.cryptType == CRYPT_ZIP2) {
						ZipCrypto c = new ZipCrypto();
						c.init(ZipCrypto.ENCRYPT_MODE, mf.pass);
						cout = new CipherOutputStream(cout, c);

						byte[] rnd = new byte[12];
						ThreadLocalRandom.current().nextBytes(rnd);
						// check byte
						rnd[11] = (byte) (e.CRC32 >>> 24);
						cout.write(rnd);
					} else {
						za = new ZipAES();
						try {
							za.init(ZipAES.ENCRYPT_MODE, mf.pass);
						} catch (InvalidKeyException ignored) {}
						za.sendHeaders(out);

						cout = new CipherOutputStream(cout, za);
					}
				}

				if (e.method != 0) {
					Deflater def = deflater;
					def.setInput(data.list, data.arrayOffset() + data.rIndex, data.readableBytes());
					def.finish();

					while (!def.finished()) {
						int len = def.deflate(bw.list, 0, bw.list.length);
						if (len > 0) cout.write(bw.list, 0, len);
					}

					e.cSize = def.getBytesWritten();
					def.reset();
				} else {
					cout.write(data.list, data.arrayOffset() + data.rIndex, data.readableBytes());
				}

				switch (e.getEncryptType()) {
					case CRYPT_AES:
					case CRYPT_AES2:
						// 16 + 2 + 10
						e.cSize += 28;
						za.sendTrailers(out);
						break;
					case CRYPT_ZIP2:
						e.cSize += 12;
						break;
				}

				long curr = r.position();
				// backward, 文件就是这点好
				r.seek(offset + 18);
				// 不用管ZIP64，这可是 byte[] 啊
				r.writeInt(Integer.reverseBytes((int) e.cSize));
				r.seek(curr);
			} else {
				SafeInputStream in = new SafeInputStream((InputStream) mf.data);
				if (in.available() == Integer.MAX_VALUE) mf.flag |= EntryMod.E_LARGE;

				e.cSize = e.uSize = mf.large() ? U32_MAX : 0;
				if (mf.cryptType == CRYPT_ZIP2) e.flags |= GP_HAS_EXT;
				long offset = r.position() + 14;
				writeLOC(out, bw, e);

				ZipAES za = null;
				OutputStream cout = out;
				if (mf.cryptType != CRYPT_NONE) {
					if (mf.cryptType == CRYPT_ZIP2) {
						ZipCrypto c = new ZipCrypto();
						c.init(ZipCrypto.ENCRYPT_MODE, mf.pass);
						cout = new CipherOutputStream(cout, c);

						byte[] rnd = new byte[12];
						ThreadLocalRandom.current().nextBytes(rnd);
						// check byte
						rnd[11] = (byte) (e.modTime >>> 8);
						cout.write(rnd);
					} else {
						za = new ZipAES();
						try {
							za.init(ZipAES.ENCRYPT_MODE, mf.pass);
						} catch (InvalidKeyException ignored) {}
						za.sendHeaders(out);

						cout = new CipherOutputStream(cout, za);
					}
				}

				byte[] list = bw.list;

				if (e.method != 0) {
					Deflater def = deflater;
					final int d2 = list.length / 2;

					int read;
					do {
						read = in.read(list, 0, d2);
						if (read < 0) break;
						def.setInput(list, 0, read);
						crc.update(list, 0, read);
						while (!def.needsInput()) {
							int len = def.deflate(list, d2, list.length - d2);
							cout.write(list, d2, len);
						}
					} while (true);
					def.finish();

					while (!def.finished()) {
						int len = def.deflate(list, 0, list.length);
						cout.write(list, 0, len);
					}

					e.uSize = def.getBytesRead();
					e.cSize = def.getBytesWritten();
					def.reset();
				} else {
					long sum = 0;
					int r;
					while (true) {
						r = in.read(list);
						if (r < 0) break;
						crc.update(list, 0, r);
						cout.write(list, 0, r);
						sum += r;
					}
					e.uSize = e.cSize = sum;
				}
				in.close();
				if (in.getException() != null) {
					in.getException().printStackTrace();
				}

				switch (e.getEncryptType()) {
					case CRYPT_AES:
					case CRYPT_AES2:
						// 16 + 2 + 10
						e.cSize += 28;
						za.sendTrailers(out);
						break;
					case CRYPT_ZIP2:
						e.cSize += 12;
						writeEXT(out, bw, e);
						break;
				}

				long curr = r.position();

				if ((Math.max(e.cSize, e.uSize) >= U32_MAX) && !mf.large())
					throw new ZipException("Zip64预测失败，对于'可能'超过4GB大小的文件,请设置它的large标志位或令in.available返回Integer.MAX_VALUE");

				r.seek(offset);
				if ((e.mzfFlag & MZ_NOCRC) == 0) {
					r.writeInt(Integer.reverseBytes((int) crc.getValue()));
					e.CRC32 = (int) crc.getValue();
				} else {
					r.writeInt(0);
				}
				if (!mf.large()) {
					r.writeInt(Integer.reverseBytes((int) e.cSize));
					r.writeInt(Integer.reverseBytes((int) e.uSize));
				} else {
					// update ZIP64
					r.seek(offset+20+e.nameBytes.length);
					r.writeLong(Long.reverseBytes(e.uSize));
					r.writeLong(Long.reverseBytes(e.cSize));
				}

				r.seek(curr);
			}

			e.offset += e.extraLenOfLOC;
		}

		bw.clear();

		end.cDirOffset = r.position();

		// 排序CEN
		int v = bw.list.length - 256;
		// 原方法更慢，离大谱
		Object[] list = entries.values().toArray();
		Arrays.sort(list, Helpers.cast(CEN_SORTER));
		for (int i = 0; i < list.length; i++) {
			writeCEN(bw, (ZEntry) list[i]);
			if (bw.wIndex() > v) {
				bw.writeToStream(out);
				bw.clear();
			}
		}
		modified.clear();

		end.cDirLen = r.position()+bw.wIndex()-end.cDirOffset;
		end.cDirTotal = entries.size();
		writeEND(bw, end, r.position()+bw.wIndex());

		bw.writeToStream(out);
		bw.clear();

		r.setLength(r.position());
	}

	private static void checkName(EntryMod entry) {
		String name = entry.name;
		if (name.contains("\\") || name.startsWith("/")) throw new IllegalArgumentException("name="+name);
		if (name.endsWith("/") && entry.data instanceof ByteList && ((ByteList) entry.data).isReadable())
			throw new IllegalArgumentException("目录不是空的");
	}

	private static void writeLOC(OutputStream os, ByteList buf, ZEntry file) throws IOException {
		buf.putInt(HEADER_LOC)
		   .putShortLE(file.getVersionFW())
		   .putShortLE(file.flags)
		   .putShortLE(file.getMethodFW())
		   .putIntLE(file.modTime)
		   .putIntLE(file.getCRC32FW())
		   .putIntLE((int) file.cSize)
		   .putIntLE((int) file.uSize)
		   .putShortLE(file.nameBytes.length)
		   .putShortLE(0) // extra
		   .put(file.nameBytes);
		file.writeLOCExtra(buf, buf.wIndex(), buf.wIndex() - file.nameBytes.length - 2);

		buf.writeToStream(os);
		buf.clear();
	}
	private static void writeEXT(OutputStream os, ByteList buf, ZEntry file) throws IOException {
		if ((file.flags & GP_HAS_EXT) == 0) throw new ZipException("Not has ext set");
		buf.putInt(HEADER_EXT).putIntLE(file.CRC32);
		if (file.cSize >= U32_MAX | file.uSize >= U32_MAX) {
			buf.putLongLE(file.cSize).putLongLE(file.uSize);
			file.EXTLenOfLOC = 24;
		} else {
		   buf.putIntLE((int) file.cSize).putIntLE((int) file.uSize);
		   file.EXTLenOfLOC = 16;
		}
		buf.writeToStream(os);
		buf.clear();
	}
	static void writeCEN(ByteList buf, ZEntry file) {
		int extLenOff = buf.wIndex() + 30;
		buf.putInt(HEADER_CEN)
		   .putShortLE(VER_MZF)
		   .putShortLE(file.getVersionFW())
		   .putShortLE(file.flags)
		   .putShortLE(file.getMethodFW())
		   .putIntLE(file.modTime).putIntLE(file.getCRC32FW())
		   .putIntLE((int) file.cSize).putIntLE((int) file.uSize)
		   .putShortLE(file.nameBytes.length)
		   .putShortLE(0) // ext
		   .putShortLE(0) // comment
		   .putShortLE(0) // disk
		   .putShortLE(file.internalAttr).putIntLE(file.externalAttr)
		   .putIntLE((int) file.startPos())
		   .put(file.nameBytes);
		int extOff = buf.wIndex();
		file.writeCENExtra(buf, extLenOff);
		buf.putShortLE(extLenOff, buf.wIndex()-extOff);
	}
	static void writeEND(ByteList util, END eof, long position) {
		boolean zip64 = false;
		int co;
		if (eof.cDirOffset >= U32_MAX) {
			co = (int) U32_MAX;
			zip64 = true;
		} else {
			co = (int) eof.cDirOffset;
		}

		int cl;
		if (eof.cDirLen >= U32_MAX) {
			cl = (int) U32_MAX;
			zip64 = true;
		} else {
			cl = (int) eof.cDirLen;
		}

		int count = eof.cDirTotal;
		if (count >= 0xFFFF) {
			count = 0xFFFF;
			zip64 = true;
		}

		if (zip64) {
			util.putInt(HEADER_ZIP64_END).putLongLE(44).putShortLE(45).putShortLE(45) // size, ver, ver
				.putIntLE(0).putIntLE(0) // disk id, attr begin id
				.putLongLE(count).putLongLE(count) // disk entries, total entries
				.putLongLE(eof.cDirLen).putLongLE(eof.cDirOffset)

				.putInt(HEADER_ZIP64_END_LOCATOR).putInt(0) // eof disk id
				.putLongLE(position).putIntLE(1); // disk in total
		}

		util.putInt(HEADER_END)
			.putShortLE(0)
			.putShortLE(0)
			.putShortLE(count)
			.putShortLE(count)
			.putIntLE(cl)
			.putIntLE(co)
			.putShortLE(eof.comment.length)
			.put(eof.comment);
	}
}
