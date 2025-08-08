package roj.archive.qz;

import org.jetbrains.annotations.NotNull;
import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveUtils;
import roj.concurrent.TaskGroup;
import roj.io.CRC32InputStream;
import roj.collect.*;
import roj.crypt.CRC32;
import roj.io.*;
import roj.io.BufferPool;
import roj.io.source.Source;
import roj.io.source.SourceInputStream;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static roj.archive.qz.BlockId.*;
import static roj.reflect.Unaligned.U;

/**
 * 我去除了大部分C++的味道，但是保留了一部分，这样你才知道自己用的是Java
 * 已支持多线程解压
 * @author Roj233
 * @since 2022/3/14 7:09
 */
public class QZArchive extends QZReader implements ArchiveFile {
	private WordBlock[] blocks;
	private QZEntry[] entries;
	private HashMap<String, QZEntry> byName;

	public static final byte FLAG_RECOVERY = 1, FLAG_SKIP_METADATA = 2, FLAG_DUMP_HIDDEN = 4;
	private final byte[] password;
	private int maxFileCount = 0xFFFFF, memoryLimitKb = 131072;

	public QZArchive(String path) throws IOException { this(new File(path), null); }
	public QZArchive(File file) throws IOException { this(file, null); }
	public QZArchive(File file, String pass) throws IOException {
		r = ArchiveUtils.tryOpenSplitArchive(file, true);
		password = pass == null ? null : pass.getBytes(StandardCharsets.UTF_16LE);
		reload();
	}

	public QZArchive(Source s) throws IOException { this(s, null); }
	public QZArchive(Source s, String pass) throws IOException {
		r = s;
		r.seek(0);
		password = pass == null ? null : pass.getBytes(StandardCharsets.UTF_16LE);
		reload();
	}
	public QZArchive(Source s, int recovery, int maxFileCount, byte[] pass) {
		r = s;
		this.flag = (byte) recovery;
		this.password = pass;
		this.maxFileCount = maxFileCount;
		this.entries = new QZEntry[0];
	}

	public void setMemoryLimitKb(int v) { memoryLimitKb = v; }

	public Source getFile() { return r; }
	public final boolean isEmpty() { return entries.length == 0; }
	public WordBlock[] getWordBlocks() { return blocks; }

	public QZFileWriter append() throws IOException {
		if (!r.isWritable()) throw new IllegalStateException("Source ["+r+"] not writable");
		if ((flag&FLAG_SKIP_METADATA) != 0) throw new IllegalStateException("Metadata skip");

		QZFileWriter fw = new QZFileWriter(r);

		for (QZEntry ent : entries) {
			if (ent.uSize > 0) fw.files.add(ent);
			else fw.emptyFiles.add(ent);
		}

		if (blocks != null) {
			fw.blocks._setArray(blocks);
			fw.blocks._setSize(blocks.length);

			WordBlock b = blocks[blocks.length-1];
			r.seek(b.offset+b.size());
		} else {
			r.seek(32);
		}

		fw.recountFlags();
		return fw;
	}

	public final void reload() throws IOException {
		if (byName == null) byName = new HashMap<>();
		else byName.clear();
		entries = null;

		var cache = (Source) U.getAndSetReference(this, CACHE, r);
		if (cache != r) IOUtil.closeSilently(cache);

		try {
			Loader loader = new Loader(this);
			loader.load();
			blocks = loader.blocks;
			entries = loader.files;
		} catch (Exception e) {
			close();
			throw e;
		}
	}

	public static final long QZ_HEADER = 0x377abcaf271c_00_02L;
	private static final class Loader {
		// -- IN --
		final QZArchive that;
		private boolean strictMode;

		Loader(QZArchive archive) { this.that = archive; strictMode = !archive.isRecovering(); }

		// -- OUT --
		private long offset;
		private long[] streamLen;

		//共享以减少无用对象
		final HashSet<Object[]> coders = new HashSet<>(Hasher.array(Object[].class));
		final QZCoder[] temp = new QZCoder[32];
		final Int2IntBiMap pipe1 = new Int2IntBiMap();
		final int[] sorted1 = new int[32];

		WordBlock[] blocks;
		QZEntry[] files;

		// -- INTERNAL --
		private ByteList buf;
		private InputStream rawIn;
		private MyDataInputStream in;

		final void load() throws IOException {
			streamLen = null;
			blocks = null;
			files = null;
			that.r.seek(0);
			rawIn = that.r.asInputStream();
			buf = (ByteList) BufferPool.buffer(false, 256);

			try {
				ByteList buf = read(32);

				if ((buf.readLong() >>> 16) != (QZ_HEADER >>> 16))
					throw new CorruptedInputException("文件头错误"+Long.toHexString(buf.readLong(0)>>>16));
				buf.rIndex = 6;

				int major = buf.readUnsignedByte();
				int minor = buf.readUnsignedByte();
				if (major != 0 || minor > 4)
					throw new UnsupportedOperationException("不支持的版本"+major+"."+minor);

				buf.rIndex = 12;
				long offset = buf.readLongLE();
				long length = buf.readLongLE();
				if ((offset|length) < 0 || offset+length > that.r.length()) {
					throw new CorruptedInputException("目录表偏移错误"+offset+'+'+length+",len="+that.r.length());
				}

				int crc = CRC32.crc32(buf.list, buf.arrayOffset()+12, 20);
				int myCrc = buf.readIntLE(8);
				if (crc != myCrc) {
					// https://www.7-zip.org/recover.html : if crc, offset and length are zero
					if (that.isRecovering()) {
						if ((offset|length|myCrc) == 0) {
							recoverFT();
							return;
						}
					} else {
						throw new CorruptedInputException("文件头校验错误"+Integer.toHexString(crc)+"/"+Integer.toHexString(myCrc));
					}
				}

				if (length > that.maxFileCount) throw new IOException("文件表过大"+length);
				if (length == 0) {
					files = new QZEntry[0];
					return;
				}

				myCrc = buf.readIntLE();

				// header + offset
				that.r.seek(32+offset);

				InputStream in = rawIn;
				if (in instanceof SourceInputStream sin) {
					sin.doClose = false;
					sin.remain = length;
				} else {
					in = new LimitInputStream(in, length);
				}

				this.in = new MyDataInputStream(rawIn = that.isRecovering() ? in : new CRC32InputStream(in, myCrc));
				readHeader();
				if ((that.flag & FLAG_DUMP_HIDDEN) != 0) findSurprise(32+offset, length);
			} finally {
				IOUtil.closeSilently(buf);
				IOUtil.closeSilently(in);
			}
		}

		private long ecOff, ecLen;
		private void findSurprise(long ehOff, long ehLen) throws IOException {
			if (ecLen != 0 && in.position() != ecLen) System.out.println("EncodedHeader存在剩余信息");

			IntervalPartition<IntervalPartition.Wrap<String>> tree = new IntervalPartition<>();

			tree.add(new IntervalPartition.Wrap<>("SignatureHeader", 0, 32));
			tree.add(new IntervalPartition.Wrap<>("Header", ehOff, ehOff+ehLen));
			if (blocks != null) {
				WordBlock lastBlock = blocks[blocks.length - 1];
				tree.add(new IntervalPartition.Wrap<>("PackedStreams", this.offset, lastBlock.offset+lastBlock.size));
			}
			if (ecOff != 0) {
				tree.add(new IntervalPartition.Wrap<>("PackedStreamsForHeaders", ecOff, ecOff+ecLen));
			}

			long hasInfo = 0;
			for (var region : tree) {
				if (hasInfo != 0) {
					System.out.println("文件的["+hasInfo+","+region.pos()+"]有一些隐藏信息");
					hasInfo = 0;
				}

				if (region.coverage().size() != 1) {
					if (region.coverage().size() > 1)
						throw new CorruptedInputException("overlapped region (wb + end header ??)");
					if (region.pos() != that.r.length())
						hasInfo = region.pos();
				}
			}

			if (hasInfo != 0) {
				System.out.println("文件的["+hasInfo+","+that.r.length()+"]有一些隐藏信息");
			}
		}

		private void recoverFT() throws IOException {
			Source r = that.r;
			long pos = r.length()-2;
			long min = Math.min(pos-1048576, 0); // 1MB

			while (pos > min) {
				r.seek(pos);

				int id = r.read();
				if (id == kEncodedHeader || id == kHeader) {
					r.seek(pos);
					try (var in = new MyDataInputStream(rawIn)) {
						this.in = in;
						readHeader();
					} catch (Exception ignored) {}
					return;
				}

				pos--;
			}
		}

		// region 实现细节
		private void readHeader() throws IOException {
			int id = in.readUnsignedByte();
			if (id == kEncodedHeader) {
				readEncodedHeader();
				id = in.readUnsignedByte();
			}

			if (id != kHeader) fatalError();
			id = in.readUnsignedByte();

			if (id == kArchiveProperties) {
				readProperties();
				id = in.readUnsignedByte();
			}

			if (id == kAdditionalStreamsInfo) {
				readStreamInfo();
				id = in.readUnsignedByte();
				throw new UnsupportedOperationException("kAdditionalStreamsInfo=="+this);
			}

			if (id == kMainStreamsInfo) {
				readStreamInfo();
				id = in.readUnsignedByte();
			}

			if (id != kFilesInfo) fatalError();

			readFileMeta();
			computeOffset();

			id = in.readUnsignedByte();
			end(id);
		}
		private void readEncodedHeader() throws IOException {
			readStreamInfo();

			if (blocks.length != 1) throw new CorruptedInputException("元数据错误");
			if (files != null) error("files != null");

			computeOffset();
			var b = blocks[0];
			blocks = null;
			ecOff = b.offset;
			ecLen = b.size;

			in.close();

			try {
				in = new MyDataInputStream(that.getSolidStream(b, null, false));
			} catch (Exception e) {
				if (b.hasProcessor(QzAES.class) && that.password != null)
					throw new CorruptedInputException("压缩包打开失败，可能是密码错误", e);
				throw e;
			}
		}

		private void readStreamInfo() throws IOException {
			int id = in.readUnsignedByte();

			if (id != kPackInfo) {
				if (id == 0) return;
				fatalError();
			}
			// region 压缩流
			offset = 32+readVarLong();

			int count = readVarInt(that.maxFileCount);

			id = in.readUnsignedByte();
			// 压缩流偏移
			if (id != kSize) fatalError();

			long[] off = streamLen = new long[count];
			for (int i = 0; i < count; i++) off[i] = readVarLong();

			id = in.readUnsignedByte();
			if (id == kCRC) {
				BitSet hasCrc = readBitsOrTrue(count);
				// int32(LE)
				in.skipForce((long) hasCrc.size() << 2);

				id = in.readUnsignedByte();
			}

			end(id);
			id = in.readUnsignedByte();
			// endregion
			if (id != kUnPackInfo) fatalError();
			// region 字块
			id = in.readUnsignedByte();
			if (id != kFolder) fatalError();

			count = readVarInt();

			if (in.readByte() != 0) error("kFolder.external");

			var blocks = this.blocks = new WordBlock[count];
			var tempSorter = new int[count][];
			for (int i = 0; i < count; i++) blocks[i] = readBlock(tempSorter, i);

			id = in.readUnsignedByte();
			if (id != kCodersUnPackSize) fatalError();

			for (int k = 0; k < blocks.length; k++) {
				var b = blocks[k];
				var cc = b.complexCoder();
				int uSizeId = cc != null ? cc.getUncompressedSizeIndex(b) : b.hasCrc >>> 2;

				long[] out = b.outSizes;
				int i = 0;
				for (int j = 0; j <= out.length; j++) {
					long size = readVarLong();
					if (j == uSizeId) b.uSize = size;
					else out[i++] = size;
				}

				var sorter = tempSorter[k];
				if (sorter != null) {
					var sortedSize = new long[out.length];
					for (i = 0; i < out.length; i++)
						sortedSize[i] = out[sorter[i]];
					b.outSizes = sortedSize;
				}
			}

			id = in.readUnsignedByte();
			if (id == kCRC) {
				BitSet set = readBitsOrTrue(count);
				for (IntIterator itr = set.iterator(); itr.hasNext(); ) {
					WordBlock b = blocks[itr.nextInt()];
					b.hasCrc |= 1; // uncompressed crc
					b.crc = in.readIntLE();
				}

				id = in.readUnsignedByte();
			}

			end(id);
			id = in.readUnsignedByte();
			// endregion
			if (id == kSubStreamsInfo) {
				// 字块到文件数据
				readFileDataMeta();
				id = in.readUnsignedByte();
			}// 可能是文件头

			end(id);
		}
		private WordBlock readBlock(int[][] sorterArr, int thisId) throws IOException {
			WordBlock b = new WordBlock();

			int ioLen = readVarInt(32);
			if (ioLen == 0) error("没有coder");

			QZCoder[] coders = temp;
			for (int i = 0; i < ioLen; i++) {
				int flag = in.readUnsignedByte();

				boolean complex = (flag & 0x10) != 0;
				if (complex) return readComplexBlock(coders, b, i, ioLen, flag);

				boolean alternate = (flag & 0x80) != 0;
				if (alternate) error("coder["+i+"].alternate");

				var c = coders[i] = QZCoder.create(read(flag&0xF));

				boolean prop = (flag & 0x20) != 0;
				if (prop) {
					int len = readVarInt(0xFF);
					long except = in.position() + len;
					c.readOptions(read(len), len);
					if (in.position() != except) error("coder配置字节有剩余");
				}
			}

			int pipeCount = ioLen-1;
			var pipe = pipe1; pipe.clear();
			// 物理顺序index到排序后顺序val的映射
			var sortedIds = sorted1;

			if (pipeCount > 0) {
				b.outSizes = new long[pipeCount];
				// 2024/7/24 注释 这里可以检测部分管道错误：Int2IntMap的KV都不能重复，所以不能添加任意一侧在map中的管道
				for (int i = pipeCount; i > 0; i--) {
					sortedIds[i] = -1;
					pipe.put(readVarInt(pipeCount), readVarInt(pipeCount));
				}
			}

			var sorted = new QZCoder[ioLen];
			for (int i = 0; i < ioLen; i++) {
				if (!pipe.containsKey(i)) {
					int sortId = 0, id = i;

					// 2024/7/24 注释 这里检测剩余所有的管道错误
					while (sortId < ioLen) {
						sortedIds[id] = sortId;
						sorted[sortId++] = coders[id];
						id = pipe.getByValueOrDefault(id, -1);
					}
				} else if (!pipe.containsValue(i)) {
					b.hasCrc |= i<<2;
				}
			}

			// 如果正好和解压路径一样（这也是正常文件的行为）那么可以省下一个数组
			for (int i = 0; i < ioLen; i++) {
				if (sortedIds[i] != i) {
					sorterArr[thisId] = Arrays.copyOf(sortedIds, ioLen);
					break;
				}
			}

			// 排序之后是从零开始的解压路径
			b.coder = (QZCoder[]) this.coders.intern(sorted);
			return b;
		}
		private WordBlock readComplexBlock(QZCoder[] coders, WordBlock b, int i, int ioLen, int flag) throws IOException {
			List<IntMap.Entry<CoderInfo>>
				inputs = new ArrayList<>(),
				outputs = new ArrayList<>();

			var ordered = new CoderInfo[ioLen];
			for (int j = 0; j < i; j++) {
				QZCoder c = coders[j];
				CoderInfo info = new CoderInfo(c, j);
				ordered[j] = info;
				IntMap.Entry<CoderInfo> entry = new IntMap.Entry<>(0, info);
				inputs.add(entry);
				outputs.add(entry);
			}

			for (;;) {
				boolean alternate = (flag & 0x80) != 0;
				if (alternate) error("coder["+i+"].alternate");

				var c = QZCoder.create(read(flag&0xF));
				var node = new CoderInfo(c, outputs.size());
				ordered[i] = node;

				boolean complex = (flag & 0x10) != 0;
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

				boolean prop = (flag & 0x20) != 0;
				if (prop) {
					int len = readVarInt(0xFF);
					long except = in.position() + len;
					c.readOptions(read(len), len);
					if (in.position() != except) error("coder配置字节有剩余");
				}

				if (++i == ioLen) break;

				flag = in.readUnsignedByte();
			}
			// 2024/07/13 以后再intern吧
			b.coder = ordered;

			int inCount = inputs.size();
			int outCount = outputs.size();
			int pipeCount = outCount-1;
			// only have one 'actual' output
			if (pipeCount < 0) throw new CorruptedInputException("too few output");
			// but can have many inputs
			if (inCount <= pipeCount) throw new CorruptedInputException("too few input");

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
				var entry = inputs.set(readVarInt(inCount), null);
				entry.getValue().setFileInput(j, entry.getIntKey());
			}

			for (int j = 0; j < outputs.size(); j++) {
				var entry = outputs.get(j);
				if (entry != null) {
					b.complexCoder = entry.getValue();
					return b;
				}
			}

			throw new CorruptedInputException("no output specified");
		}

		// 文件的大小偏移等"数据的元数据"
		private void readFileDataMeta() throws IOException {
			int neFiles;

			int nid = in.readUnsignedByte();
			if (nid == kNumUnPackStream) {
				neFiles = 0;
				for (WordBlock b : blocks)
					neFiles += b.fileCount = readVarInt();

				nid = in.readUnsignedByte();
			} else {
				neFiles = blocks.length;
				for (WordBlock b : blocks)
					b.fileCount = 1;
			}

			if (neFiles > that.maxFileCount) error("文件数量超出限制");
			QZEntry[] files = this.files = new QZEntry[neFiles];
			boolean skipMetadata = (that.flag&FLAG_SKIP_METADATA) != 0;
			for (int i = 0; i < neFiles; i++)
				files[i] = skipMetadata ? new QZEntry() : new QZEntryA();

			int fileId = 0;
			if (nid == kSize) {
				WordBlock[] blocks = this.blocks;
				for (int j = 0; j < blocks.length; j++) {
					WordBlock b = blocks[j];

					QZEntry prev = null;
					long sum = 0;
					for (int i = b.fileCount-1; i > 0; i--) {
						long size = readVarLong();

						QZEntry f = files[fileId++];
						f.block = b;
						f.offset = sum;
						f.uSize = size;

						if (prev != null) prev.next = f;
						else b.firstEntry = f;
						prev = f;

						sum += size;
					}

					QZEntry f = files[fileId++];
					f.block = b;
					f.offset = sum;
					f.uSize = b.uSize - sum;

					if (prev != null) prev.next = f;
					else b.firstEntry = f;

					if (f.uSize <= 0) error("block["+j+"]没有足够的数据:最后的解压大小为" + f.uSize);
				}

				nid = in.readUnsignedByte();
			} else {
				if (neFiles != blocks.length) fatalError();

				for (WordBlock b : blocks) {
					QZEntry f = files[fileId++];

					f.block = b;
					f.offset = 0;
					f.uSize = b.uSize;

					b.firstEntry = f;

					if ((b.hasCrc&1) != 0) f._setCrc(b.crc);
				}
			}

			if (nid == kCRC) {
				int extraCrc = 0;
				for (WordBlock b : blocks) {
					if (b.fileCount != 1 || (b.hasCrc&1) == 0) {
						extraCrc += b.fileCount;
					}
				}

				BitSet extraCrcs = readBitsOrTrue(extraCrc);

				fileId = 0;
				extraCrc = 0;

				for (WordBlock b : blocks) {
					if (b.fileCount == 1 && (b.hasCrc&1) != 0) {
						files[fileId++]._setCrc(b.crc);
					} else {
						for (int i = b.fileCount; i > 0; i--) {
							if (extraCrcs.contains(extraCrc++)) {
								int crc = in.readIntLE();
								files[fileId++]._setCrc(crc);

								if (b.fileCount == 1) {
									b.hasCrc |= 1;
									b.crc = crc;
								}
							}
						}
					}
				}

				nid = in.readUnsignedByte();
			}

			end(nid);
		}

		private void readProperties() throws IOException {
			int nid = in.readUnsignedByte();
			while (nid != kEnd) {
				long size = readVarLong();
				if ((that.flag&FLAG_DUMP_HIDDEN) != 0)
					System.out.println("扩展属性:"+nid+",长度为"+size);
				in.skipForce(size);
				nid = in.readUnsignedByte();
			}
		}
		// 文件名称，空文件，文件夹，修改时间等元数据
		private void readFileMeta() throws IOException {
			boolean skipMetadata = (that.flag&FLAG_SKIP_METADATA) != 0;
			int fileCount = readVarInt(that.maxFileCount);
			QZEntry[] files = this.files;
			int nonEmptyFileCount;

			if (files == null) {
				nonEmptyFileCount = 0;
				// 全部是空文件
				files = new QZEntry[fileCount];
				for (int i = 0; i < fileCount; i++) files[i] = skipMetadata ? new QZEntry() : new QZEntryA();
			} else {
				nonEmptyFileCount = files.length;
				if (fileCount != files.length) {
					if (fileCount < files.length) fatalError();

					// 有一些空文件
					files = new QZEntry[fileCount];
					for (int i = nonEmptyFileCount; i < fileCount; i++) files[i] = skipMetadata ? new QZEntry() : new QZEntryA();
				}
			}

			int id = in.readUnsignedByte();
			BitSet empty = null;
			if (id == kEmptyStream) {
				if (fileCount == nonEmptyFileCount) fatalError();

				int length = in.readVUInt();
				BitSet emptyFile = null, anti = null;
				empty = BitSet.readBits(in, fileCount);
				while (true) {
					id = in.readUnsignedByte();
					if (id == kEmptyFile) {
						if (emptyFile != null) error("kEmptyFile属性重复");
						length = in.readVUInt();
						emptyFile = BitSet.readBits(in, empty.size());
					} else if (id == kAnti) {
						if (anti != null) error("kAnti属性重复");
						length = in.readVUInt();
						anti = BitSet.readBits(in, empty.size());
					} else {
						break;
					}
				}

				// 重排序空文件
				int emptyNo = this.files == null ? 0 : this.files.length, fileNo = 0;
				int emptyNoNo = 0;
				for (int i = 0; i < fileCount; i++) {
					if (!empty.contains(i)) {
						if (fileNo == emptyNo) fatalError();
						// noinspection all
						files[i] = this.files[fileNo++];
						// 明显的，uSize不可能等于0
					} else {
						// 同样uSize必然等于0
						QZEntry entry = files[i] = files[emptyNo++];

						if (emptyFile == null || !emptyFile.contains(emptyNoNo)) entry.flag |= QZEntry.DIRECTORY;
						if (anti != null && anti.contains(emptyNoNo)) entry.flag |= QZEntry.ANTI;

						emptyNoNo++;
					}
				}
			} else if (nonEmptyFileCount != fileCount) {
				if (strictMode) error("!L 属性缺失或恶意构造的文件, 添加RECOVERY标志位来接受, 极大内存开销, 注意拒绝服务攻击");
				relocationAll(id, files, nonEmptyFileCount, fileCount, skipMetadata);
				return;
			}

			while (id != 0) {
				int len = readVarInt();
				switch (id) {
					case kEmptyFile, kAnti -> {
						if (nonEmptyFileCount == fileCount) fatalError();
						if (strictMode) error("!L 属性缺失或恶意构造的文件, 添加RECOVERY标志位来接受, 极大内存开销, 注意拒绝服务攻击");
						if (id == kEmptyFile) relocation15(in, files, empty);
						else relocation16(in, files, empty);
					}
					case kName -> {
						if (in.readByte() != 0) error("iFileName.external");

						len >>= 1;
						int j = 0;

						CharList sb = IOUtil.getSharedCharBuf();
						// UTF-16 LE
						for (int i = 0; i < len; i++) {
							int c = in.readUShortLE();
							if (c == 0) {
								files[j++].name = sb.toString();
								sb.clear();
							} else {
								sb.append((char) c);
							}
						}

						if (j != fileCount) error("文件名太少");
					}
					case kCTime, kATime, kMTime -> {
						if (skipMetadata) {in.skipForce(len);continue;}

						BitSet set = readBitsOrTrue(fileCount);
						if (in.readByte() != 0) error("i_Time.external");

						long off = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[id-kCTime];
						int flag = 1 << (id-kCTime);

						for (var itr = set.iterator(); itr.hasNext(); ) {
							var entry = files[itr.nextInt()];
							entry.flag |= flag;
							U.putLong(entry, off, in.readLongLE());
						}
					}
					case kWinAttributes -> {
						if (skipMetadata) {in.skipForce(len);continue;}

						BitSet set = readBitsOrTrue(fileCount);
						if (in.readByte() != 0) error("iAttribute.external");

						long off = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[3];
						for (var itr = set.iterator(); itr.hasNext(); ) {
							var entry = files[itr.nextInt()];
							entry.flag |= QZEntry.ATTR;
							U.putInt(entry, off, in.readIntLE());
						}
					}
					default -> in.skipForce(len);
				}

				id = in.readUnsignedByte();
			}

			this.files = files;
		}

		private void relocation15(MyDataInput in, QZEntry[] files, BitSet empty) throws IOException {
			var emptyFile = BitSet.readBits(in, empty.size());
			int j = 0;
			for (var itr = empty.iterator(); itr.hasNext(); ) {
				int i = itr.nextInt();
				if (emptyFile.contains(j++)) {
					files[i].flag &= ~QZEntry.DIRECTORY;
				}
			}
		}
		private void relocation16(MyDataInput in, QZEntry[] files, BitSet empty) throws IOException {
			var anti = BitSet.readBits(in, empty.size());
			int j = 0;
			for (var itr = empty.iterator(); itr.hasNext(); ) {
				int i = itr.nextInt();
				if (anti.contains(j++)) {
					files[i].flag |= QZEntry.ANTI;
				}
			}
		}
		@SuppressWarnings("fallthrough")
		private void relocationAll(int id, QZEntry[] files, int nonEmptyFileCount, int fileCount, boolean skipMetadata) throws IOException {
			byte[][] deferredAttributes = new byte[8][];
			BitSet empty = null;
			while (id != 0) {
				int len = in.readVUInt();
				switch (id) {
					case kEmptyStream:
						empty = BitSet.readBits(in, fileCount);
						int emptyNo = nonEmptyFileCount, fileNo = 0;
						for (int i = 0; i < fileCount; i++) {
							if (!empty.contains(i)) {
								if (fileNo == emptyNo) fatalError();
								files[i] = this.files[fileNo++];
							} else {
								files[i] = files[emptyNo++];
							}
						}
					break;
					case kCTime, kATime, kMTime, kWinAttributes, kComment:
						if (skipMetadata) {
							in.skipForce(len);
							break;
						}
					case kEmptyFile, kAnti, kName:
						deferredAttributes[id - kEmptyFile] = in.readBytes(len);
					break;
					default:in.skipForce(len);break;
				}

				id = in.readUnsignedByte();
			}

			if (empty == null) fatalError();

			for (id = kEmptyFile; id < kComment; id++) {
				byte[] attribute = deferredAttributes[id-kEmptyFile];
				if (attribute != null) {
					var in = DynByteBuf.wrap(attribute);
					int len = attribute.length;

					switch (id) {
						case kEmptyFile -> relocation15(in, files, empty);
						case kAnti -> relocation16(in, files, empty);
						case kName -> {
							if (in.readByte() != 0) error("iFileName.external");

							len >>= 1;
							int j = 0;

							CharList sb = IOUtil.getSharedCharBuf();
							// UTF-16 LE
							for (int i = 0; i < len; i++) {
								int c = in.readUShortLE();
								if (c == 0) {
									files[j++].name = sb.toString();
									sb.clear();
								} else {
									sb.append((char) c);
								}
							}

							if (j != fileCount) error("文件名太少");
						}
						case kCTime, kATime, kMTime, kWinAttributes -> {
							BitSet set;
							// all true
							if (in.readByte() != 0) {
								set = new BitSet();
								set.fill(fileCount);
							} else {
								set = BitSet.readBits(in, fileCount);
							}

							if (in.readByte() != 0) error("i_Time.external");

							long off = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[id-kCTime];

							if (id == kWinAttributes) {
								for (var itr = set.iterator(); itr.hasNext(); ) {
									var entry = files[itr.nextInt()];
									entry.flag |= QZEntry.ATTR;
									U.putInt(entry, off, in.readIntLE());
								}
							} else {
								int flag = 1 << (id-kCTime);

								for (var itr = set.iterator(); itr.hasNext(); ) {
									var entry = files[itr.nextInt()];
									entry.flag |= flag;
									U.putLong(entry, off, in.readLongLE());
								}
							}
						}
					}
				}
			}
		}

		private void computeOffset() throws IOException {
			var blocks = this.blocks;
			if (blocks == null) return;

			long off = offset;
			long length = that.r.length();
			int streamId = 0;
			for (int i = 0; i < blocks.length; i++) {
				var b = blocks[i];

				b.offset = off;

				long[] offs = b.extraSizes;
				for (int j = -1; j < offs.length; j++) {
					long len = streamLen[streamId++];
					if (j < 0) b.size = len;
					else offs[j] = len;
					off += len;
				}

				if (off > length) error("字块["+i+"](子流"+streamId+")越过文件边界("+off+" > "+length+")");
			}
		}

		private ByteList read(int len) throws IOException {
			var b = buf;
			b.clear();
			IOUtil.readFully(in == null ? rawIn : in, b.list, b.arrayOffset(), len);
			b.wIndex(len);
			return b;
		}
		private BitSet readBitsOrTrue(int size) throws IOException {
			BitSet set;
			// all true
			if (in.readByte() != 0) {
				set = new BitSet();
				set.fill(size);
			} else {
				set = BitSet.readBits(in, size);
			}
			return set;
		}
		private long readVarLong() throws IOException {
			long i = in.readVULong();
			if (i < 0) error("溢出:"+i);
			return i;
		}
		private int readVarInt() throws IOException {
			long i = in.readVULong();
			if (i < 0 || i > Integer.MAX_VALUE) error("溢出:"+i);
			return (int) i;
		}
		private int readVarInt(int max) throws IOException {
			int i = readVarInt();
			if (i > max && !that.isRecovering()) error("长度超出限制");
			return i;
		}
		private void end(int type) throws IOException {
			if (type != 0) error("期待0,"+type);
		}

		private static void fatalError() throws IOException { throw new CorruptedInputException(); }
		private void error(String msg) throws IOException { if (!that.isRecovering()) throw new CorruptedInputException(msg); }

		// endregion
	}

	public boolean isRecovering() {return (flag & FLAG_RECOVERY) != 0;}

	@Override
	public QZEntry getEntry(String name) { return getEntries().get(name); }
	@Deprecated
	public HashMap<String, QZEntry> getEntries() {
		if (byName.isEmpty()) {
			byName.ensureCapacity(entries.length);
			for (QZEntry entry : entries) {
				if (byName.putIfAbsent(entry.name, entry) != null) {
					throw new IllegalArgumentException("文件名重复！该文件可能已损坏，请通过entries()获取Entry并读取");
				}
			}
		}
		return byName;
	}

	@Override
	public List<QZEntry> entries() { return Arrays.asList(entries); }
	public QZEntry[] getEntriesByPresentOrder() { return entries; }

	public void parallelDecompress(TaskGroup th, BiConsumer<QZEntry, InputStream> callback) {parallelDecompress(th, callback, password);}
	public void parallelDecompress(TaskGroup th, BiConsumer<QZEntry, InputStream> callback, byte[] pass) {
		if (blocks == null) return;

		Objects.requireNonNull(r, "Stream Closed");
		for (WordBlock b : blocks) {
			th.executeUnsafe(() -> {
				if (r == null) throw new FastFailException("其他线程关闭了压缩包");

				try (var in = getSolidStream(b, pass, false)) {
					// noinspection all
					LimitInputStream lin = new LimitInputStream(in, 0, false);
					QZEntry entry = b.firstEntry;
					long toSkip = 0;
					do {
						if (th.isCancelled()) return;

						lin.remain = entry.uSize;

						InputStream fin = lin;
						if ((entry.flag&QZEntry.CRC) != 0) fin = new CRC32InputStream(fin, entry.crc32);
						if (toSkip > 0) fin = new SkipInputStream(fin, in, toSkip);

						callback.accept(entry, fin);
						if (fin instanceof SkipInputStream x) toSkip = x.toSkip;
						toSkip += lin.remain;

						entry = entry.next;
					} while (entry != null);
				}
			});
		}
	}

	private static final class SkipInputStream extends InputStream {
		private final InputStream in, rin;
		long toSkip;

		private SkipInputStream(InputStream in, InputStream rin, long skip) {
			this.in = in;
			this.rin = rin;
			toSkip = skip;
		}

		@Override
		public int read() throws IOException {
			if (toSkip != 0) doSkip();
			return in.read();
		}
		@Override
		public int read(@NotNull byte[] b, int off, int len) throws IOException {
			if (toSkip != 0) doSkip();
			return in.read(b, off, len);
		}

		private void doSkip() throws IOException {
			long skip = rin.skip(toSkip);
			if (skip < toSkip) throw new EOFException("数据流过早终止");
			toSkip = 0;
		}
	}


	public final InputStream getStream(String entry) throws IOException {
		QZEntry file = getEntries().get(entry);
		if (file == null) return null;
		return getInput(file, null);
	}
	@Override
	public final InputStream getStream(ArchiveEntry entry, byte[] pw) throws IOException { return getInput((QZEntry) entry, pw); }

	final InputStream getSolidStream1(WordBlock b, byte[] pass, Source src, QZReader that, boolean verify) throws IOException {
		if (pass == null) pass = password;
		assert blocks == null || Arrays.asList(blocks).contains(b) : "foreign word block "+b;

		src.seek(b.offset);

		var limit = new AtomicInteger(memoryLimitKb);
		InputStream in;
		var node = b.complexCoder();
		if (node == null) {
			in = new SourceInputStream.Shared(src, b.size, that, CACHE);

			var coders = b.coder;
			QZCoder.useMemory(limit, coders.length);
			for (int i = 0; i < coders.length; i++) {
				in = coders[i].decode(in, pass, i==coders.length-1?b.uSize:b.outSizes[i], limit);
			}
		} else {
			long[] sizes = b.extraSizes;
			var streams = new InputStream[sizes.length+1];
			QZCoder.useMemory(limit, streams.length);

			long off = b.offset;
			src.seek(off);
			// noinspection all
			streams[0] = new SourceInputStream.Shared(src, b.size, that, CACHE);
			off += b.size;

			for (int i = 0; i < sizes.length;) {
				src = src.copy();
				src.seek(off);

				long len = sizes[i++];
				// noinspection all
				streams[i] = new SourceInputStream(src, len);
				off += len;
			}

			var ins = node.getInputStream(b.outSizes, streams, new HashMap<>(), pass, limit);
			if (ins.length != 1) throw new CorruptedInputException("root node has many outputs");
			in = ins[0];
		}

		// 这个可以清理掉，因为QZEntry自身有CRC32了
		// 不过我喜欢搞骚操作，也许直接读WordBlock了
		if (verify && !isRecovering() && (b.hasCrc&1) != 0) in = new CRC32InputStream(in, b.crc);

		return in;
	}

	public synchronized QZReader parallel() {
		if (asyncReaders.isEmpty()) asyncReaders = new ArrayList<>();
		AsyncReader ar = new AsyncReader();
		ar.r = r;
		asyncReaders.add(ar);
		return ar;
	}
	private final class AsyncReader extends QZReader {
		@Override
		InputStream getSolidStream1(WordBlock b, byte[] pass, Source src, QZReader that, boolean verify) throws IOException {
			return QZArchive.this.getSolidStream1(b, pass, src, that, verify);
		}

		@Override
		public void close() throws IOException {
			synchronized (QZArchive.this) { asyncReaders.remove(this); }
			closeSolidStream();
		}
	}
	private List<AsyncReader> asyncReaders = Collections.emptyList();

	@Override
	public synchronized void close() throws IOException {
		closeSolidStream();

		for (AsyncReader reader : asyncReaders) {
			var cache = (Source) U.getAndSetReference(reader, CACHE, null);
			IOUtil.closeSilently(cache);

			reader.closeSolidStream();
		}
		asyncReaders.clear();

		if (r != null) {
			Source r1 = r;
			r1.close();
			r = null;

			Source cache = (Source) U.getAndSetReference(this, CACHE, null);
			IOUtil.closeSilently(cache);

			if (password != null) Arrays.fill(password, (byte) 0);
		}
	}
}