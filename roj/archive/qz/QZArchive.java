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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;
import java.util.zip.CRC32;

import static roj.archive.qz.BlockId.*;

/**
 * 我去除了大部分C++的味道，但是保留了一部分，这样你才知道自己用的是Java
 * 已支持多线程解压
 * @author Roj233
 * @since 2022/3/14 7:09
 */
public class QZArchive implements ArchiveFile {
	Source r;
	private final BufferPool pool;
	private ByteList buf = ByteList.EMPTY;

	WordBlock[] blocks;
	QZEntry[] entries;
	private final MyHashMap<String, QZEntry> byName = new MyHashMap<>();

	private boolean recovery;
	private final byte[] password;
	private int maxFileCount = 0xFFFFF, memoryLimitKb = 131072;

	public QZArchive(File file) throws IOException { this(file, null); }
	public QZArchive(File file, String pass) throws IOException {
		if (!file.isFile()) throw new FileNotFoundException(file.getName());

		password = pass == null ? null : pass.getBytes(StandardCharsets.UTF_16LE);
		pool = BufferPool.localPool();

		r = new FileSource(file);
		if (file.getName().endsWith(".001")) {
			r = BufferedSource.autoClose(new SplittedSource((FileSource) r, (int) file.length()));
		}

		reload();
	}

	public QZArchive(Source s) throws IOException { this(s, null); }
	public QZArchive(Source s, String pass) throws IOException {
		password = pass == null ? null : pass.getBytes(StandardCharsets.UTF_16LE);
		pool = BufferPool.localPool();

		r = s;
		r.seek(0);
		reload();
	}
	public QZArchive(Source s, boolean recovery, int maxFileCount, byte[] pass, BufferPool pool) {
		r = s;
		this.recovery = recovery;
		this.password = pass;
		this.pool = pool;
		this.maxFileCount = maxFileCount;
	}

	// region File

	public void setMemoryLimitKb(int v) { memoryLimitKb = v; }
	public void setMaxFileCount(int v) { maxFileCount = v; }

	public Source getFile() { return r; }
	public final boolean isEmpty() { return entries == null; }
	public WordBlock[] getWordBlocks() { return blocks; }

	@Override
	public synchronized void close() throws IOException {
		closeSolidStream();

		if (r != null) {
			r.close();
			r = null;

			if (password != null) Arrays.fill(password, (byte) 0);
		}
	}

	@SuppressWarnings("deprecation")
	public QZFileWriter append() throws IOException {
		QZFileWriter fw = new QZFileWriter(r);

		if (entries != null) {
			for (QZEntry ent : entries) {
				if (ent.uSize > 0) fw.files.add(ent);
				else fw.emptyFiles.add(ent);
			}
		}

		if (blocks != null) {
			fw.blocks.setRawArray(blocks);
			fw.blocks.i_setSize(blocks.length);

			WordBlock b = blocks[blocks.length-1];
			r.seek(b.offset+b.size());
		} else {
			r.seek(32);
		}

		fw.countFlags();
		return fw;
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
				throw new IOException("目录表偏移错误"+offset+'+'+length+",len="+r.length());
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
			buf.close();
		}
	}
	private void recoverFT() throws IOException {
		long pos = r.length()-2;
		long min = Math.min(pos-1048576, 0); // 1MB

		while (pos > min) {
			r.seek(pos);

			int id = r.read();
			if (id == kEncodedHeader || id == kHeader) {
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
		if (id == kEncodedHeader) {
			readCompressedFT();
			id = buf.readUnsignedByte();
		}

		if (id != kHeader) throw new IOException("无效的文件表");
		id = buf.readUnsignedByte();

		if (id == kArchiveProperties) {
			readProperties();
			id = buf.readUnsignedByte();
		}

		if (id == kAdditionalStreamsInfo) {
			//QzInfo additionalInfo = readStreamInfo();
			//id = buf.readUnsignedByte();
			fatalError();
		}

		QzInfo si;
		if (id == kMainStreamsInfo) {
			si = readStreamInfo();
			id = buf.readUnsignedByte();
		} else {
			si = new QzInfo();
		}

		if (id != kFilesInfo) fatalError();

		readFileMetas(si);
		if (si.files != null && si.files.length > 0) {
			entries = si.files;
			blocks = si.blocks;
		}

		computeOffset(si);

		id = buf.readUnsignedByte();
		end(id);
		return si;
	}
	private void readCompressedFT() throws IOException {
		QzInfo d = readStreamInfo();

		if (d.blocks == null || d.blocks.length == 0)
			throw new IOException("❌ 头部错误");

		computeOffset(d);
		WordBlock b = d.blocks[0];

		buf.close();

		try (InputStream in = getSolidStream("<header>", new BufferedSource(r, 1024, pool, false), b, null)) {
			buf = (ByteList) pool.buffer(false, (int) b.uSize);
			int read = buf.readStream(in, (int) b.uSize);
			if (read < b.uSize) throw new EOFException("数据流过早终止");
			if (in.read() >= 0) error("unknown padding");
		}
	}

	private void readProperties() throws IOException {
		int nid = buf.readUnsignedByte();
		while (nid != kEnd) {
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
			if (count < files.length) fatalError();

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

			int end = readVarInt()+buf.rIndex;
			switch (id) {
				case kEmptyStream: empty = MyBitSet.readBits(buf, count); break;
				case kEmptyFile: emptyFile = MyBitSet.readBits(buf, Objects.requireNonNull(empty, "属性顺序错误").size()); break;
				case kAnti: anti = MyBitSet.readBits(buf, Objects.requireNonNull(empty, "属性顺序错误").size()); break;
				default: buf.rIndex = end; break;
			}
			assert buf.rIndex == end;
		}

		// 重排序空文件
		if (empty != null) {
			int emptyNo = d.files == null ? 0 : d.files.length, fileNo = 0;
			int emptyNoNo = 0;
			for (int i = 0; i < count; i++) {
				if (!empty.contains(i)) {
					if (fileNo == emptyNo) fatalError();
					// noinspection all
					files[i] = d.files[fileNo++];
					// 明显的，uSize不可能等于0
				} else {
					// 同样uSize必然等于0
					QZEntry entry = files[i] = files[emptyNo++];

					if (emptyFile == null || !emptyFile.contains(emptyNoNo)) entry.flag |= QZEntry.DIRECTORY;
					if (anti != null && anti.contains(emptyNoNo)) entry.flag |= QZEntry.ANTI;

					emptyNoNo++;
				}
			}
		}

		buf.rIndex = pos;
		while (true) {
			int id = buf.readUnsignedByte();
			if (id == 0) break;

			int len = readVarInt();
			switch (id) {
				case kName: {
					if (buf.get() != 0) error("iFileName.external");

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
				case kCTime:
				case kATime:
				case kMTime: {
					MyBitSet set = readBitsOrTrue(count);
					if (buf.get() != 0) error("i_Time.external");

					ObjLongConsumer<QZEntry> c = attributeFiller[id-kCTime];

					for (IntIterator itr = set.iterator(); itr.hasNext(); ) {
						c.accept(files[itr.nextInt()], buf.readLongLE());
					}
				}
				break;
				case kWinAttributes: {
					MyBitSet set = readBitsOrTrue(count);
					if (buf.get() != 0) error("iAttribute.external");

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

		long off = si.offset;
		int streamId = 0;
		for (int i = 0; i < blocks.length; i++) {
			WordBlock b = blocks[i];

			b.offset = off;

			long[] offs = b.extraSizes;
			for (int j = -1; j < offs.length; j++) {
				long len = si.streamLen[streamId++];
				if (j < 0) b.size = len;
				else offs[j] = len;
				off += len;
			}

			if (off > r.length()) error("字块["+i+"](子流"+streamId+")越过文件边界");
		}
	}

	private QzInfo readStreamInfo() throws IOException {
		QzInfo si = new QzInfo();

		int id = buf.readUnsignedByte();

		if (id != kPackInfo) {
			if (id == 0) return si;
			fatalError();
		}
		// region 压缩流
		si.offset = 32+readVarLong();

		int count = readVarInt(maxFileCount);

		id = buf.readUnsignedByte();
		// 压缩流偏移
		if (id != kSize) fatalError();

		long[] off = si.streamLen = new long[count];
		for (int i = 0; i < count; i++) off[i] = readVarLong();

		id = buf.readUnsignedByte();
		if (id == kCRC) {
			MyBitSet hasCrc = readBitsOrTrue(count);
			// int32(LE)
			buf.rIndex += hasCrc.size() * 4;

			id = buf.readUnsignedByte();
		}

		end(id);
		id = buf.readUnsignedByte();
		// endregion
		if (id != kUnPackInfo) fatalError();
		// region 字块
		id = buf.readUnsignedByte();
		if (id != kFolder) fatalError();

		count = readVarInt();

		if (buf.get() != 0) error("iWordBlock.external");

		WordBlock[] blocks = si.blocks = new WordBlock[count];
		for (int i = 0; i < count; i++) blocks[i] = readBlock();

		id = buf.readUnsignedByte();
		if (id != kCodersUnPackSize) fatalError();

		for (WordBlock b : blocks) {
			int uSizeId = b.complexCoder != null
				? b.complexCoder.setUSizeId(b)
				: b.hasCrc >>> 2;

			long[] out = b.outSizes;
			int i = 0;
			for (int j = 0; j <= out.length; j++) {
				long size = readVarLong();
				if (j == uSizeId) b.uSize = size;
				else out[i++] = size;
			}

			if (b.complexCoder == null && out.length > 0) {
				long[] sortedSize = new long[out.length];
				Int2IntMap sorter = ((Int2IntMap) b.tmp);
				for (i = 0; i < out.length; i++)
					sortedSize[i] = out[sorter.getOrDefaultInt(i,-1)];

				b.outSizes = sortedSize;

				// no use
				b.tmp = null;
			}
		}

		id = buf.readUnsignedByte();
		if (id == kCRC) {
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
		if (id == kSubStreamsInfo) {
			// 字块到文件数据
			readBlockFileMap(si);
			id = buf.readUnsignedByte();
		}// 可能是文件头

		end(id);
		return si;
	}
	private WordBlock readBlock() throws IOException {
		WordBlock b = new WordBlock();

		int ioLen = readVarInt(32);
		if (ioLen == 0) error("没有coder");
		QZCoder[] coders = new QZCoder[ioLen];

		for (int i = 0; i < ioLen; i++) {
			int flag = buf.readUnsignedByte();

			boolean complex = (flag & 0x10) != 0;
			boolean prop = (flag & 0x20) != 0;
			boolean alternative = (flag & 0x80) != 0;
			if (alternative) error("coder["+i+"].alternative");

			if (complex) {
				buf.rIndex--;
				return readComplexBlock(coders, b, i);
			}

			QZCoder c = coders[i] = QZCoder.create(buf, flag&0xF);

			if (prop) {
				int len = readVarInt(0xFF);
				int ri = buf.rIndex;
				c.readOptions(buf, len);
				buf.rIndex = ri+len;
			}
		}

		if (ioLen > 1) b.outSizes = new long[ioLen-1];

		int pipeCount = ioLen-1;
		Int2IntBiMap pipe = new Int2IntBiMap(pipeCount);
		for (int i = pipeCount; i > 0; i--) {
			pipe.putInt(readVarInt(ioLen), readVarInt(ioLen));
		}

		Int2IntMap sorter = new Int2IntMap();
		b.tmp = sorter;
		QZCoder[] sorted = b.coder = new QZCoder[ioLen];
		for (int i = 0; i < ioLen; i++) {
			if (!pipe.containsKey(i)) {
				int sortId = 0, id = i;

				while (sortId < ioLen) {
					sorter.put(id, sortId);
					sorted[sortId++] = coders[id];
					id = pipe.getByValueOrDefault(id, -1);
				}
			} else if (!pipe.containsValue(i)) {
				b.hasCrc |= i<<2;
			}
		}
		return b;
	}
	private WordBlock readComplexBlock(QZCoder[] coders, WordBlock b, int i) throws IOException {
		List<IntMap.Entry<CoderInfo>>
			inputs = new SimpleList<>(),
			outputs = new SimpleList<>();

		CoderInfo[] ordered = new CoderInfo[coders.length];
		for (int j = 0; j < i; j++) {
			QZCoder c = coders[j];
			CoderInfo info = new CoderInfo(c, j);
			ordered[j] = info;
			IntMap.Entry<CoderInfo> entry = new IntMap.Entry<>(0, info);
			inputs.add(entry);
			outputs.add(entry);
		}
		b.tmp = ordered;

		for (; i < coders.length; i++) {
			int flag = buf.readUnsignedByte();

			boolean complex = (flag & 0x10) != 0;
			boolean prop = (flag & 0x20) != 0;
			boolean alternative = (flag & 0x80) != 0;
			if (alternative) error("coder["+i+"].alternative");

			QZCoder c = QZCoder.create(buf, flag&0xF);
			CoderInfo node = new CoderInfo(c, outputs.size());
			ordered[i] = node;

			if (complex) {
				int in = readVarInt(8);
				int out = readVarInt(8);
				for (int j = 0; j < in; j++) inputs.add(new IntMap.Entry<>(j, node));
				for (int j = 0; j < out; j++) outputs.add(new IntMap.Entry<>(j, node));
			} else {
				IntMap.Entry<CoderInfo> entry = new IntMap.Entry<>(0, node);
				inputs.add(entry);
				outputs.add(entry);
			}

			if (prop) {
				int len = readVarInt(0xFF);
				int ri = buf.rIndex;
				c.readOptions(buf, len);
				buf.rIndex = ri+len;
			}
		}

		int inCount = inputs.size();
		int outCount = outputs.size();
		int pipeCount = outCount-1;
		// only have one 'actual' output
		if (pipeCount < 0) throw new IOException("too few output");
		// but can have many inputs
		if (inCount <= pipeCount) throw new IOException("too few input");

		b.outSizes = new long[outCount-1];

		for (i = pipeCount; i > 0; i--) {
			IntMap.Entry<CoderInfo>
				in = inputs.set(readVarInt(inCount), null),
				out = outputs.set(readVarInt(outCount), null);

			in.getValue().pipe(in.getIntKey(), out.getValue(), out.getIntKey());
		}

		int dataCount = inCount-pipeCount;
		b.extraSizes = new long[dataCount-1];
		for (int j = 0; j < dataCount; j++) {
			IntMap.Entry<CoderInfo> entry = inputs.set(readVarInt(inCount), null);
			entry.getValue().setFileInput(j, entry.getIntKey());
		}

		for (int j = 0; j < outputs.size(); j++) {
			IntMap.Entry<CoderInfo> entry = outputs.get(j);
			if (entry != null) {
				b.complexCoder = entry.getValue();
				break;
			}
		}

		assert b.complexCoder != null;
		return b;
	}

	private void readBlockFileMap(QzInfo si) throws IOException {
		int neFiles;

		int nid = buf.readUnsignedByte();
		if (nid == kNumUnPackStream) {
			neFiles = 0;
			for (WordBlock b : si.blocks)
				neFiles += b.fileCount = readVarInt();

			nid = buf.readUnsignedByte();
		} else {
			neFiles = si.blocks.length;
			for (WordBlock b : si.blocks)
				b.fileCount = 1;
		}

		if (neFiles > maxFileCount) error("文件数量超出限制");
		si.files = new QZEntry[neFiles];
		for (int i = 0; i < neFiles; i++)
			si.files[i] = new QZEntry();

		int fileId = 0;
		if (nid == kSize) {
			WordBlock[] blocks = si.blocks;
			for (int j = 0; j < blocks.length; j++) {
				WordBlock b = blocks[j];

				QZEntry prev = null;
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

				if (f.uSize <= 0) error("block["+j+"]没有足够的数据:最后的解压大小为" + f.uSize);
			}

			nid = buf.readUnsignedByte();
		} else {
			if (neFiles != si.blocks.length) fatalError();

			for (WordBlock b : si.blocks) {
				QZEntry f = si.files[fileId++];

				f.block = b;
				f.offset = 0;
				f.uSize = b.uSize;

				b.firstEntry = f;

				if ((b.hasCrc&1) != 0) f.setCrc(b.crc);
			}
		}

		if (nid == kCRC) {
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
		if (i < 0) throw new IOException("溢出:"+i);
		return i;
	}
	private int readVarInt() throws IOException {
		long i = buf.readVULong();
		if (i < 0 || i > Integer.MAX_VALUE) throw new IOException("溢出:"+i);
		return (int) i;
	}
	private int readVarInt(int max) throws IOException {
		int i = readVarInt();
		if (i > max && !recovery) error("长度超出限制");
		return i;
	}
	private void end(int type) throws IOException {
		if (type != 0) throw new IOException("期待0,"+type);
	}

	private static void fatalError() throws IOException { throw new IOException("数据错误"); }
	private void error(String msg) throws IOException { if (!recovery) throw new IOException(msg); }

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
		if (blocks == null) return;
		for (WordBlock b : blocks) {
			th.pushTask(() -> {
				Source r = this.r.threadSafeCopy();
				Source src = r.isBuffered() ? r : new BufferedSource(r, 1024, pool, true);

				try (InputStream in = getSolidStream("block" + b.offset, src, b, pass)) {
					// noinspection all
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
					// noinspection all
					activeIn.skip(activeIn.remain);
					activeIn.remain = e.uSize;
					// noinspection all
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

		if (!recovery && (file.flag&QZEntry.CRC) != 0) return new ChecksumInputStream(fin, new CRC32(), file.crc32&0xFFFFFFFFL);
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
		if (pass == null) pass = password;

		src.seek(b.offset);
		InputStream in;
		if (b.complexCoder == null) {
			in = new SourceInputStream(src, b.size);

			QZCoder[] coders = b.coder;
			for (int i = 0; i < coders.length; i++) {
				QZCoder c = coders[i];
				in = c.decode(in, pass, i==coders.length-1?b.uSize:b.outSizes[i], memoryLimitKb);
			}
		} else {
			CoderInfo node = b.complexCoder;
			InputStream[] streams = new InputStream[b.extraSizes.length+1];

			long off = b.offset;
			src.seek(off);
			// noinspection all
			streams[0] = new SourceInputStream(src, b.size);
			off += b.size;

			for (int i = 0; i < b.extraSizes.length;) {
				src = src.threadSafeCopy();
				src.seek(off);

				long len = b.extraSizes[i++];
				// noinspection all
				streams[i] = new SourceInputStream(src, len);
				off += len;
			}

			InputStream[] ins = node.getInputStream(b.outSizes, streams, new MyHashMap<>(), pass, memoryLimitKb);
			assert ins.length == 1 : "root node has many outputs";
			in = ins[0];
		}

		if (!recovery && (b.hasCrc&1) != 0) in = new ChecksumInputStream(in, new CRC32(), b.crc&0xFFFFFFFFL);

		return in;
	}

	static final class QzInfo {
		long offset;
		long[] streamLen;

		WordBlock[] blocks;

		QZEntry[] files;
	}
}
