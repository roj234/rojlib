package roj.archive.qz;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ChecksumInputStream;
import roj.collect.*;
import roj.concurrent.TaskHandler;
import roj.crypt.CRCAny;
import roj.io.IOUtil;
import roj.io.LimitInputStream;
import roj.io.SourceInputStream;
import roj.io.buf.BufferPool;
import roj.io.source.BufferedSource;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.io.source.SplittedSource;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;
import java.util.zip.CRC32;

import static roj.archive.qz.BlockId.*;

/**
 * 我去除了大部分C++的味道，但是保留了一部分，这样你才知道自己用的是Java
 * 已支持多线程解压
 * hint: 7z的密码是UTF_16LE的
 * @author Roj233
 * @since 2022/3/14 7:09
 */
public class QZArchive implements ArchiveFile {
	public File file;

	Source r;
	private final BufferPool pool;
	private ByteList buf = ByteList.EMPTY;

	private WordBlock[] blocks;
	private QZEntry[] entries;
	private final MyHashMap<String, QZEntry> byName = new MyHashMap<>();

	private boolean recovery;
	private byte[] password;
	int maxFileCount = 0xFFFFF;

	public QZArchive(File file) throws IOException {
		this(file, (byte[]) null);
	}
	public QZArchive(File file, String pass) throws IOException {
		this(file, pass.getBytes(StandardCharsets.UTF_16LE));
	}

	public QZArchive(File file, byte[] pass) throws IOException {
		this.file = file;
		this.password = pass;
		pool = BufferPool.localPool();

		r = new FileSource(file);
		if (file.getName().endsWith(".001")) {
			r = BufferedSource.autoClose(new SplittedSource((FileSource) r, (int) file.length()));
		}

		init();
	}

	public QZArchive(Source s, String pass) throws IOException {
		this(s, pass.getBytes(StandardCharsets.UTF_16LE));
	}
	public QZArchive(Source s, byte[] pass) throws IOException {
		r = s;
		password = pass;
		pool = BufferPool.localPool();

		init();
	}
	public QZArchive(Source s, boolean recovery, byte[] pass, BufferPool pool) throws IOException {
		r = s;
		this.recovery = recovery;
		this.password = pass;
		this.pool = pool;

		init();
	}

	// region File

	public Source getFile() {
		return r;
	}

	public final boolean isEmpty() {
		return entries == null;
	}

	@Override
	public synchronized void close() throws IOException {
		closeSolidStream();

		if (r != null) {
			r.close();
			r = null;

			if (password != null) Arrays.fill(password, (byte) 0);
		}
	}

	// endregion
	// region Load

	static final long QZ_HEADER = 0x377abcaf271c_00_02L;
	static final ObjLongConsumer<QZEntry>[] attributeFiller = Helpers.cast(new ObjLongConsumer<?>[4]);
	static {
		attributeFiller[0] = (k, v) -> {
			k.flag |= QZEntry.CT;
			k.createTime = v;
		};
		attributeFiller[1] = (k, v) -> {
			k.flag |= QZEntry.AT;
			k.accessTime = v;
		};
		attributeFiller[2] = (k, v) -> {
			k.flag |= QZEntry.MT;
			k.modifyTime = v;
		};
	}

	private void init() throws IOException {
		if (r.length() >= 32) {
			r.seek(0);
			reload();
		} else if (r.length() == 0) {
			r.setLength(32);
			r.writeLong(QZ_HEADER);
			// CRC32
			r.writeInt(Integer.reverseBytes(265657229));
		} else {
			throw new IOException("头部错误");
		}
	}

	public final void reload() throws IOException {
		byName.clear();
		entries = null;

		try {
			ByteList buf = read(32);

			if ((buf.readLong() >>> 16) != (QZ_HEADER >>> 16))
				throw new IOException("文件头错误"+Long.toHexString(buf.readLong(0)>>>16));
			buf.rIndex = 6;

			int major = buf.readUnsignedByte();
			int minor = buf.readUnsignedByte();
			if (major != 0 || minor > 4)
				throw new IOException("不支持的版本"+major+"."+minor);

			buf.rIndex = 12;
			long offset = buf.readLongLE();
			long length = buf.readLongLE();
			if ((offset|length) < 0 || offset+length > r.length()) {
				throw new IOException("目录表偏移错误"+offset+'+'+length);
			}

			int crc = CRCAny.CRC_32.defVal();
			crc = CRCAny.CRC_32.update(crc, buf.list, buf.arrayOffset()+12, 20);
			crc = CRCAny.CRC_32.retVal(crc);
			int myCrc = buf.readIntLE(8);
			if (crc != myCrc) {
				// https://www.7-zip.org/recover.html : if crc, offset and length are zero
				if ((offset|length|myCrc) == 0 && recovery) {
					recoverFT();
					return;
				}
				throw new IOException("文件头校验错误"+Integer.toHexString(crc)+"/"+Integer.toHexString(myCrc));
			}

			if (length > maxFileCount) throw new IOException("文件表过大"+length);
			if (length == 0) return;

			myCrc = buf.readIntLE();

			// header + offset
			r.seek(32+offset);
			buf = read((int) length);

			crc = CRCAny.CRC_32.defVal();
			crc = CRCAny.CRC_32.update(crc, buf.list, buf.arrayOffset(), buf.wIndex());
			crc = CRCAny.CRC_32.retVal(crc);
			if (crc != myCrc) throw new IOException("文件表校验错误"+Integer.toHexString(crc)+"/"+Integer.toHexString(myCrc));

			readFileTable();
		} finally {
			if (buf.capacity() > 0) pool.reserve(buf);
		}
	}
	private void recoverFT() throws IOException {
		long pos = r.length()-2;
		long min = Math.min(pos-1048576, 0); // 1MB

		while (pos > min) {
			r.seek(pos);

			int id = r.read();
			if (id == iPackedHeader || id == iHeader) {
				try {
					r.seek(pos);
					read((int) (r.length()-pos));
					readFileTable();
				} catch (Exception ignored) {}
				return;
			}

			pos--;
		}
	}

	private QzInfo readFileTable() throws IOException {
		int id = buf.readUnsignedByte();
		if (id == iPackedHeader) {
			readCompressedFT();
			id = buf.readUnsignedByte();
		}

		if (id != iHeader) throw new IOException("无效的文件表");
		id = buf.readUnsignedByte();

		if (id == iProp) {
			readProperties();
			id = buf.readUnsignedByte();
		}

		if (id == iMoreStream) throw new UnsupportedOperationException("MoreStream");

		if (id != iArchiveInfo) throw new IOException("trap");

		QzInfo si = readStreamInfo();
		id = buf.readUnsignedByte();

		if (id != iFilesInfo) throw new IOException("trap");

		readFileMetas(si);
		computeOffset(si);
		id = buf.readUnsignedByte();

		end(id);

		if (si.files != null && si.files.length > 0) {
			entries = si.files;
			blocks = si.blocks;
		}
		return si;
	}
	private void readCompressedFT() throws IOException {
		QzInfo d = readStreamInfo();

		if (d.blocks == null || d.blocks.length == 0)
			throw new IOException("❌ 头部错误");

		computeOffset(d);
		WordBlock b = d.blocks[0];

		pool.reserve(buf);
		buf = ByteList.EMPTY;

		try (InputStream in = getSolidStream("<header>", new BufferedSource(r, 1024, pool, false), b, null)) {
			buf = (ByteList) pool.buffer(false, (int) b.uSize);
			buf.readStreamFully(in);

			if (buf.wIndex() < b.uSize) throw new EOFException("数据流过早终止");
			buf.wIndex((int) b.uSize);
		}
	}

	private void readProperties() throws IOException {
		int nid = buf.readUnsignedByte();
		while (nid != iEnd) {
			long size = readVarLong();
			buf.rIndex += size;
			nid = buf.readUnsignedByte();
		}
	}
	private void readFileMetas(QzInfo d) throws IOException {
		int count = readVarInt(maxFileCount);
		QZEntry[] files = d.files;

		if (files == null) {
			// 全部是空文件
			files = new QZEntry[count];
			for (int i = 0; i < count; i++) {
				files[i] = new QZEntry();
			}
		} else if (count != files.length) {
			if (count < files.length) throw new IOException("数据错误");

			// 有一些空文件
			files = new QZEntry[count];
			System.arraycopy(d.files, 0, files, 0, d.files.length);
			for (int i = d.files.length; i < count; i++) files[i] = new QZEntry();
		}

		MyBitSet empty = null, emptyFile = null;
		MyBitSet anti = null;

		int pos = buf.rIndex;
		while (true) {
			int id = buf.readUnsignedByte();
			if (id == 0) break;

			int len = readVarInt();
			switch (id) {
				case iEmpty: empty = MyBitSet.readBits(buf, count); break;
				case iEmptyFile: emptyFile = MyBitSet.readBits(buf, Objects.requireNonNull(empty, "属性顺序错误").size()); break;
				case iDeleteFile: anti = MyBitSet.readBits(buf, Objects.requireNonNull(empty, "属性顺序错误").size()); break;
				default: buf.rIndex += len; break;
			}
		}

		// 重排序空文件
		if (empty != null) {
			int emptyNo = d.files == null ? 0 : d.files.length, fileNo = 0;
			for (int i = 0; i < count; i++) {
				if (!empty.contains(i)) {
					if (fileNo == emptyNo) throw new IOException("Empty数据错误");
					files[i] = d.files[fileNo++];
					// 明显的，uSize不可能等于0
				} else {
					// 同样uSize必然等于0
					QZEntry entry = files[i] = files[emptyNo++];

					if (emptyFile == null || !emptyFile.contains(emptyNo)) entry.flag |= QZEntry.DIRECTORY;
					if (anti != null && anti.contains(emptyNo)) entry.flag |= QZEntry.ANTI;
				}
			}
		}

		buf.rIndex = pos;
		while (true) {
			int id = buf.readUnsignedByte();
			if (id == 0) break;

			int len = readVarInt();
			switch (id) {
				case iFileName: {
					if (buf.get() != 0) throw new UnsupportedOperationException("external");

					int end = buf.rIndex+len-1;
					int j = 0;

					CharList sb = IOUtil.getSharedCharBuf();
					// UTF-16 LE
					while (buf.rIndex < end) {
						int c = buf.readUShortLE();
						if (c == 0) {
							files[j++].name = sb.toString();
							sb.clear();
						} else {
							sb.append((char) c);
						}
					}

					if (buf.rIndex != end || j != count)
						throw new IOException("文件名太少");
					break;
				}
				case iCTime:
				case iATime:
				case iMTime: {
					MyBitSet set = readBitsOrTrue(count);
					if (buf.get() != 0) throw new UnsupportedOperationException("external");

					ObjLongConsumer<QZEntry> c = attributeFiller[id- iCTime];
					int flag = QZEntry.CT<<(id- iCTime);

					for (IntIterator itr = set.iterator(); itr.hasNext(); ) {
						c.accept(files[itr.nextInt()], buf.readLongLE());
					}
				}
				break;
				case iAttribute: {
					MyBitSet set = readBitsOrTrue(count);
					if (buf.get() != 0) throw new UnsupportedOperationException("external");

					for (IntIterator itr = set.iterator(); itr.hasNext(); ) {
						QZEntry entry = files[itr.nextInt()];
						entry.flag |= QZEntry.ATTR;
						entry.attributes = buf.readIntLE();
					}
				}
				break;

				default: buf.rIndex += len; break;
			}
		}

		d.files = files;
	}

	private void computeOffset(QzInfo si) throws IOException {
		WordBlock[] blocks = si.blocks;
		if (blocks == null) return; // 全是空文件

		for (int i = 0; i < blocks.length; i++) {
			WordBlock b = blocks[i];
			int next = b.inputTargets.length-1;

			if (next == 0 && si.crcExist != null && si.crcExist.contains(i)) {
				b.hasCrc |= 2; // compressed crc
				b.cCrc = si.crc[i];
			}

			long begin = i==0?0:si.sizeSum[i-1];
			b.offset = begin + si.offset;
			b.size = si.sizeSum[i+=next] - begin;

			if (b.offset < 0 || b.offset + b.size > r.length())
				throw new IOException("字块["+i+"]长度错误");
		}
	}

	private QzInfo readStreamInfo() throws IOException {
		QzInfo si = new QzInfo();

		int id = buf.readUnsignedByte();

		if (id != iStreamInfo) {
			if (id == 0) return si;
			throw new IOException("trap");
		}
		// region 压缩流
		si.offset = 32+readVarLong();

		int count = readVarInt(maxFileCount);

		id = buf.readUnsignedByte();
		// 压缩流偏移
		if (id != iSize) throw new IOException("trap");

		long[] off = si.sizeSum = new long[count];
		for (int i = 0; i < count; i++) {
			off[i] = readVarLong() + (i==0?0:off[i-1]);
		}

		id = buf.readUnsignedByte();
		if (id == iCRC32) {
			si.crcExist = readBitsOrTrue(count);
			int[] crc = si.crc = new int[count];
			for (IntIterator itr = si.crcExist.iterator(); itr.hasNext(); ) {
				crc[itr.nextInt()] = buf.readIntLE();
			}

			id = buf.readUnsignedByte();
		}

		end(id);
		id = buf.readUnsignedByte();
		// endregion
		if (id != iWordBlockInfo) throw new IOException("trap");
		// region 字块
		id = buf.readUnsignedByte();
		if (id != iWordBlock) throw new IOException("trap");

		count = readVarInt();

		if (buf.get() != 0) throw new UnsupportedOperationException("external");

		WordBlock[] blocks = si.blocks = new WordBlock[count];
		for (int i = 0; i < count; i++) blocks[i] = readBlock();

		id = buf.readUnsignedByte();
		if (id != iWordBlockSizes) throw new IOException("trap");

		for (WordBlock b : blocks) {
			long[] out = b.outSizes;
			for (int i = 0; i < out.length; i++)
				out[i] = readVarLong();

			// usize
			b.uSize = out[b.hasCrc >>> 2];

			if (b.sortedCoders.length <= 1) {
				b.outSizes = null;
				continue;
			}

			long[] actualOutSize = new long[b.sortedCoders.length-1];
			// sort outSize
			for (int i = 0; i < b.sortedCoders.length-1; i++) {
				QZCoder c = b.sortedCoders[i];
				for (int j = 0; j < b.coders.length; j++) {
					if (b.coders[j] == c) {
						actualOutSize[i] = out[j];
						break;
					}
				}
			}
			// no used
			b.coders = null;
			b.outSizes = actualOutSize;
		}

		id = buf.readUnsignedByte();
		if (id == iCRC32) {
			MyBitSet set = readBitsOrTrue(count);
			for (IntIterator itr = set.iterator(); itr.hasNext(); ) {
				WordBlock b = blocks[itr.nextInt()];
				b.hasCrc |= 1; // uncompressed crc
				b.crc = buf.readIntLE();
			}

			id = buf.readUnsignedByte();
		}

		end(id);
		id = buf.readUnsignedByte();
		// endregion
		if (id == iBlockFileMap) {
			// 字块到文件数据
			readBlockFileMap(si);
			id = buf.readUnsignedByte();
		}// 可能是文件头

		end(id);
		return si;
	}
	private WordBlock readBlock() throws IOException {
		WordBlock b = new WordBlock();

		int inCount = 0, outCount = 0;

		QZCoder[] coders = b.coders = new QZCoder[readVarInt(32)];
		if (coders.length == 0) throw new IOException("No CODER");

		for (int i = 0; i < coders.length; i++) {
			int flag = buf.readUnsignedByte();

			boolean simple = (flag & 0x10) == 0;
			boolean prop = (flag & 0x20) != 0;
			boolean alternative = (flag & 0x80) != 0;
			if (alternative) throw new UnsupportedOperationException("alternative");

			QZCoder c = coders[i] = QZCoder.create(buf, flag&0xF);

			int in, out;
			if (simple) {
				in = 1;
				out = 1;
			} else {
				in = readVarInt(8);
				out = readVarInt(8);
			}

			inCount += in;
			outCount += out;

			if (prop) {
				int len = readVarInt(0xFF);
				int ri = buf.rIndex;
				c.readOptions(buf, len);
				buf.rIndex = ri+len;
			}
		}

		b.outSizes = new long[outCount];

		Int2IntBiMap pipe = new Int2IntBiMap();
		int pipeCount = outCount-1;

		// only have one 'actual' output
		if (pipeCount < 0) throw new IOException("too few output");
		// but can have many inputs
		if (inCount <= pipeCount) throw new IOException("too few input");

		for (int i = pipeCount; i > 0; i--) {
			// in, out
			pipe.putInt(readVarInt(inCount), readVarInt(outCount));
		}

		int dataCount = inCount-pipeCount;
		if (dataCount == 1) {
			// throw new IOException("Couldn't find stream's bind pair index");
			for (int i = 0; i < inCount; i++) {
				if (!pipe.containsKey(i)) {
					b.inputTargets = new int[] {i};
					b.sortedCoders = sortCoder(i, coders, pipe);
					break;
				}
			}
		} else {
			int[] ids = b.inputTargets = new int[dataCount];
			b.sortedCoders = sortCoder(ids[0] = readVarInt(inCount), coders, pipe);
			for (int i = 1; i < dataCount; i++) ids[i] = readVarInt(inCount);
		}

		for (int i = outCount-1; i >= 0; i--) {
			if (!pipe.containsValue(i)) {
				if (i > 63) throw new IOException("assertion failure: output id <= 63");
				b.hasCrc |= i<<2;
				break;
			}
		}

		return b;
	}
	private QZCoder[] sortCoder(int id, QZCoder[] coders, Int2IntBiMap pipe) throws IOException {
		SimpleList<QZCoder> list = new SimpleList<>(3);

		while (id >= 0 && id < coders.length) {
			if (list.contains(coders[id]))
				throw new IOException("coder cannot reuse");
			list.add(coders[id]);

			id = pipe.getByValueOrDefault(id, -1);
		}
		return list.toArray(new QZCoder[list.size()]);
	}

	private void readBlockFileMap(QzInfo si) throws IOException {
		int neFiles;

		int nid = buf.readUnsignedByte();
		if (nid == iFileCounts) {
			neFiles = 0;
			for (WordBlock b : si.blocks)
				neFiles += b.fileCount = readVarInt();

			nid = buf.readUnsignedByte();
		} else {
			neFiles = si.blocks.length;
			for (WordBlock b : si.blocks)
				b.fileCount = 1;
		}

		if (neFiles > maxFileCount) throw new IOException("文件数量超出限制");
		si.files = new QZEntry[neFiles];
		for (int i = 0; i < neFiles; i++)
			si.files[i] = new QZEntry();

		int fileId = 0;
		if (nid == iSize) {
			QZEntry prev = null;
			for (WordBlock b : si.blocks) {
				long sum = 0;
				for (int i = b.fileCount-1; i > 0; i--) {
					long size = readVarLong();

					QZEntry f = si.files[fileId++];
					f.block = b;
					f.offset = sum;
					f.uSize = size;

					if (prev != null) prev.next = f;
					else b.firstEntry = f;
					prev = f;

					sum += size;
				}

				QZEntry f = si.files[fileId++];
				f.block = b;
				f.offset = sum;
				f.uSize = b.uSize - sum;

				if (prev != null) prev.next = f;

				if (f.uSize < 0) throw new IOException("字块没有足够的数据,"+f.uSize);
			}

			nid = buf.readUnsignedByte();
		} else {
			if (neFiles != si.blocks.length) throw new IOException("trap");

			WordBlock[] blocks = si.blocks;
			for (WordBlock b : blocks) {
				QZEntry f = si.files[fileId++];

				f.block = b;
				f.offset = 0;
				f.uSize = b.uSize;

				b.firstEntry = f;

				if ((b.hasCrc&1) != 0) f.setCrc(b.crc);
			}
		}

		if (nid == iCRC32) {
			int extraCrc = 0;
			for (WordBlock b : si.blocks) {
				if (b.fileCount != 1 || (b.hasCrc&1) == 0) {
					extraCrc += b.fileCount;
				}
			}

			MyBitSet extraCrcs = readBitsOrTrue(extraCrc);

			fileId = 0;
			extraCrc = 0;

			for (WordBlock b : si.blocks) {
				if (b.fileCount == 1 && (b.hasCrc&1) != 0) {
					si.files[fileId++].setCrc(b.crc);
				} else {
					for (int i = b.fileCount; i > 0; i--) {
						if (extraCrcs.contains(extraCrc++)) {
							int crc = buf.readIntLE();
							si.files[fileId++].setCrc(crc);

							if (b.fileCount == 1) {
								b.hasCrc |= 1;
								b.crc = crc;
							}
						}
					}
				}
			}

			nid = buf.readUnsignedByte();
		}

		end(nid);
	}

	private ByteList read(int len) throws IOException {
		ByteList b = buf;
		if (b.capacity() < len) {
			if (b.capacity() > 0) pool.reserve(b);
			buf = ByteList.EMPTY;
			b = buf = (ByteList) pool.buffer(false, len);
		} else {
			b.clear();
		}
		r.readFully(b.list, b.arrayOffset(), len);
		b.wIndex(len);
		return b;
	}
	private MyBitSet readBitsOrTrue(int size) {
		MyBitSet set;
		// all true
		if (buf.get() != 0) {
			set = new MyBitSet();
			set.fill(size);
		} else {
			set = MyBitSet.readBits(buf, size);
		}
		return set;
	}
	private long readVarLong() throws IOException {
		long i = buf.readVULong();
		if (i < 0) throw new IOException("sign error:"+i);
		return i;
	}
	private int readVarInt() throws IOException {
		long i = buf.readVULong();
		if (i < 0 || i > Integer.MAX_VALUE) throw new IOException("sign error:"+i);
		return (int) i;
	}
	private int readVarInt(int max) throws IOException {
		int i = readVarInt();
		if (i > max) throw new IOException("长度超出限制");
		return i;
	}
	private void end(int type) throws IOException {
		if (type != 0) throw new IOException("期待0,"+type);
	}

	// endregion

	public MyHashMap<String, QZEntry> getEntries() {
		if (byName.isEmpty() && entries != null) {
			byName.ensureCapacity(entries.length);
			for (QZEntry entry : entries) {
				byName.put(entry.name, entry);
			}
		}
		return byName;
	}

	public InputStream getInputStream(ArchiveEntry entry, byte[] password) throws IOException {
		return getStream((QZEntry) entry, password);
	}

	public QZEntry[] getEntriesByPresentOrder() {
		return entries;
	}

	public void parallelDecompress(TaskHandler th, BiConsumer<QZEntry, InputStream> callback) { parallelDecompress(th, callback, password); }
	public void parallelDecompress(TaskHandler th, BiConsumer<QZEntry, InputStream> callback, byte[] pass) {
		for (WordBlock b : blocks) {
			th.pushTask(() -> {
				Source r = this.r.threadSafeCopy();
				Source src = r.isBuffered() ? r : new BufferedSource(r, 1024, pool, true);

				try (InputStream in = getSolidStream("block" + b.offset, src, b, pass)) {
					LimitInputStream lin = new LimitInputStream(in, 0, false);
					QZEntry entry = b.firstEntry;
					do {
						lin.remain = entry.uSize;

						InputStream fin = lin;
						if ((entry.flag&QZEntry.CRC) != 0) fin = new ChecksumInputStream(fin, new CRC32(), entry.crc32&0xFFFFFFFFL);

						callback.accept(entry, fin);
						if (lin.remain > 0) {
							if (in.skip(lin.remain) < lin.remain) throw new IOException("数据流过早终止");
						}

						entry = entry.next;
					} while (entry != null);
				}
			});
		}
	}

	public InputStream getStream(String entry) throws IOException {
		QZEntry file = getEntries().get(entry);
		if (file == null) return null;
		return getStream(file, null);
	}
	public InputStream getStream(QZEntry file) throws IOException {
		return getStream(file, null);
	}
	public InputStream getStream(QZEntry file, byte[] pass) throws IOException {
		if (file.uSize == 0) return new SourceInputStream(null, 0);

		// 顺序访问的处理
		if (activeEntry != null && activeEntry.block == file.block) {
			long size = 0;
			QZEntry e = activeEntry.next;
			while (e != null) {
				if (e == file) {
					activeEntry = file;
					// assert...
					activeIn.skip(activeIn.remain);
					activeIn.remain = e.uSize;
					blockInput.skip(size);
					return activeIn;
				}
				size += e.uSize;
				e = e.next;
			}
		}

		closeSolidStream();

		Source r = this.r.threadSafeCopy();
		InputStream in = blockInput = getSolidStream(file.name, r.isBuffered()?r:BufferedSource.autoClose(r), file.block, pass);
		if (in.skip(file.offset) < file.offset) {
			in.close();
			throw new EOFException("数据流过早终止");
		}

		LimitInputStream fin = new LimitInputStream(in, file.uSize);
		if (file.next != null) {
			activeEntry = file;
			activeIn = fin;
		}

		if ((file.flag&QZEntry.CRC) != 0) return new ChecksumInputStream(fin, new CRC32(), file.crc32&0xFFFFFFFFL);
		return fin;
	}

	private QZEntry activeEntry;
	private InputStream blockInput;
	private LimitInputStream activeIn;
	private void closeSolidStream() throws IOException {
		if (blockInput != null) {
			blockInput.close();
			blockInput = null;
		}
		activeIn = null;
		activeEntry = null;
	}

	private InputStream getSolidStream(String name, Source src, WordBlock b, byte[] pass) throws IOException {
		src.seek(b.offset);
		InputStream in = new SourceInputStream(src, b.size);

		if (pass == null) pass = password;

		QZCoder[] coders = b.sortedCoders;
		for (int i = 0; i < coders.length; i++) {
			QZCoder c = coders[i];
			in = c.decode(in, pass, i==coders.length-1?b.uSize:b.outSizes[i], 100000);
		}

		if ((b.hasCrc&1) != 0) in = new ChecksumInputStream(in, new CRC32(), b.crc&0xFFFFFFFFL);

		return in;
	}

	static final class QzInfo {
		long offset;
		long[] sizeSum;

		MyBitSet crcExist;
		int[] crc;

		WordBlock[] blocks;

		QZEntry[] files;
	}
}
