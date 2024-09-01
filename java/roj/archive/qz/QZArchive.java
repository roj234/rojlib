package roj.archive.qz;

import org.jetbrains.annotations.NotNull;
import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveUtils;
import roj.archive.CRC32InputStream;
import roj.collect.*;
import roj.concurrent.TaskHandler;
import roj.crypt.CRC32s;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.LimitInputStream;
import roj.io.SourceInputStream;
import roj.io.buf.BufferPool;
import roj.io.source.Source;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousCloseException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static roj.archive.qz.BlockId.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * 我去除了大部分C++的味道，但是保留了一部分，这样你才知道自己用的是Java
 * 已支持多线程解压
 * @author Roj233
 * @since 2022/3/14 7:09
 */
public class QZArchive extends QZReader implements ArchiveFile {
	private WordBlock[] blocks;
	private QZEntry[] entries;
	private MyHashMap<String, QZEntry> byName;

	public static final byte FLAG_RECOVERY = 1, FLAG_SKIP_METADATA = 2;
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

		fw.countFlags();
		return fw;
	}

	public final void reload() throws IOException {
		if (byName == null) byName = new MyHashMap<>();
		else byName.clear();
		entries = null;

		if (fpRead != r) {
			if (fpRead != null) fpRead.close();
			fpRead = r;
		}

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
		Loader(QZArchive archive) { this.that = archive; }

		// -- OUT --
		private long offset;
		private long[] streamLen;

		//共享以减少无用对象
		final MyHashSet<Object[]> coders = new MyHashSet<>(Hasher.array(Object[].class));
		final QZCoder[] temp = new QZCoder[32];
		final Int2IntBiMap pipe1 = new Int2IntBiMap();
		final int[] sorted1 = new int[32];

		WordBlock[] blocks;
		QZEntry[] files;

		// -- INTERNAL --
		private ByteList buf = ByteList.EMPTY;

		final void load() throws IOException {
			streamLen = null;
			blocks = null;
			files = null;
			that.r.seek(0);

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

				int crc = CRC32s.once(buf.list, buf.arrayOffset()+12, 20);
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
				buf = read((int) length);

				crc = CRC32s.once(buf.list, buf.arrayOffset(), buf.wIndex());
				if (crc != myCrc && !that.isRecovering()) throw new CorruptedInputException("元数据校验错误 got 0x"+Integer.toHexString(crc)+"/except 0x"+Integer.toHexString(myCrc));

				readHeader();
			} finally {
				buf.close();
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
					try {
						r.seek(pos);
						read((int) (r.length()-pos));
						readHeader();
					} catch (Exception ignored) {}
					return;
				}

				pos--;
			}
		}

		// region 实现细节
		private void readHeader() throws IOException {
			int id = buf.readUnsignedByte();
			if (id == kEncodedHeader) {
				readEncodedHeader();
				id = buf.readUnsignedByte();
			}

			if (id != kHeader) fatalError();
			id = buf.readUnsignedByte();

			if (id == kArchiveProperties) {
				readProperties();
				id = buf.readUnsignedByte();
			}

			if (id == kAdditionalStreamsInfo) {
				readStreamInfo();
				id = buf.readUnsignedByte();
				throw new UnsupportedOperationException("kAdditionalStreamsInfo=="+this);
			}

			if (id == kMainStreamsInfo) {
				readStreamInfo();
				id = buf.readUnsignedByte();
			}

			if (id != kFilesInfo) fatalError();

			readFileMeta();
			computeOffset();

			id = buf.readUnsignedByte();
			end(id);
		}
		private void readEncodedHeader() throws IOException {
			readStreamInfo();

			if (blocks.length != 1) throw new CorruptedInputException("元数据错误");
			if (files != null) error("files != null");

			computeOffset();
			WordBlock b = blocks[0];
			blocks = null;

			buf.close();

			try (InputStream in = that.getSolidStream(b, null, false)) {
				int size = (int) b.uSize;
				buf = (ByteList) BufferPool.buffer(false, size);
				int read = buf.readStream(in, size);
				if (read != size || in.read() >= 0) throw new EOFException("数据流过早终止");
			}
		}

		private void readStreamInfo() throws IOException {
			int id = buf.readUnsignedByte();

			if (id != kPackInfo) {
				if (id == 0) return;
				fatalError();
			}
			// region 压缩流
			offset = 32+readVarLong();

			int count = readVarInt(that.maxFileCount);

			id = buf.readUnsignedByte();
			// 压缩流偏移
			if (id != kSize) fatalError();

			long[] off = streamLen = new long[count];
			for (int i = 0; i < count; i++) off[i] = readVarLong();

			id = buf.readUnsignedByte();
			if (id == kCRC) {
				MyBitSet hasCrc = readBitsOrTrue(count);
				// int32(LE)
				buf.rIndex += hasCrc.size() << 2;

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

			if (buf.readByte() != 0) error("kFolder.external");

			var blocks = this.blocks = new WordBlock[count];
			var tempSorter = new int[count][];
			for (int i = 0; i < count; i++) blocks[i] = readBlock(tempSorter, i);

			id = buf.readUnsignedByte();
			if (id != kCodersUnPackSize) fatalError();

			for (int k = 0; k < blocks.length; k++) {
				var b = blocks[k];
				var cc = b.complexCoder();
				int uSizeId = cc != null ? cc.initSizeId(b) : b.hasCrc >>> 2;

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
				readFileDataMeta();
				id = buf.readUnsignedByte();
			}// 可能是文件头

			end(id);
		}
		private WordBlock readBlock(int[][] sorterArr, int thisId) throws IOException {
			WordBlock b = new WordBlock();

			int ioLen = readVarInt(32);
			if (ioLen == 0) error("没有coder");

			QZCoder[] coders = temp;
			for (int i = 0; i < ioLen; i++) {
				int flag = buf.readUnsignedByte();

				boolean complex = (flag & 0x10) != 0;
				boolean prop = (flag & 0x20) != 0;
				boolean alternate = (flag & 0x80) != 0;
				if (alternate) error("coder["+i+"].alternate");

				if (complex) {
					buf.rIndex--;
					return readComplexBlock(coders, b, i, ioLen);
				}

				var c = coders[i] = QZCoder.create(buf, flag&0xF);

				if (prop) {
					int len = readVarInt(0xFF);
					int ri = buf.rIndex;
					c.readOptions(buf, len);
					buf.rIndex = ri+len;
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
					pipe.putInt(readVarInt(pipeCount), readVarInt(pipeCount));
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
		private WordBlock readComplexBlock(QZCoder[] coders, WordBlock b, int i, int ioLen) throws IOException {
			List<IntMap.Entry<CoderInfo>>
				inputs = new SimpleList<>(),
				outputs = new SimpleList<>();

			var ordered = new CoderInfo[ioLen];
			for (int j = 0; j < i; j++) {
				QZCoder c = coders[j];
				CoderInfo info = new CoderInfo(c, j);
				ordered[j] = info;
				IntMap.Entry<CoderInfo> entry = new IntMap.Entry<>(0, info);
				inputs.add(entry);
				outputs.add(entry);
			}

			for (; i < ioLen; i++) {
				int flag = buf.readUnsignedByte();

				boolean complex = (flag & 0x10) != 0;
				boolean prop = (flag & 0x20) != 0;
				boolean alternate = (flag & 0x80) != 0;
				if (alternate) error("coder["+i+"].alternate");

				var c = QZCoder.create(buf, flag&0xF);
				var node = new CoderInfo(c, outputs.size());
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

			int nid = buf.readUnsignedByte();
			if (nid == kNumUnPackStream) {
				neFiles = 0;
				for (WordBlock b : blocks)
					neFiles += b.fileCount = readVarInt();

				nid = buf.readUnsignedByte();
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

				nid = buf.readUnsignedByte();
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

				MyBitSet extraCrcs = readBitsOrTrue(extraCrc);

				fileId = 0;
				extraCrc = 0;

				for (WordBlock b : blocks) {
					if (b.fileCount == 1 && (b.hasCrc&1) != 0) {
						files[fileId++]._setCrc(b.crc);
					} else {
						for (int i = b.fileCount; i > 0; i--) {
							if (extraCrcs.contains(extraCrc++)) {
								int crc = buf.readIntLE();
								files[fileId++]._setCrc(crc);

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

		private void readProperties() throws IOException {
			int nid = buf.readUnsignedByte();
			while (nid != kEnd) {
				long size = readVarLong();
				buf.rIndex += size;
				nid = buf.readUnsignedByte();
			}
		}
		// 文件名称，空文件，文件夹，修改时间等元数据
		private void readFileMeta() throws IOException {
			boolean skipMetadata = (that.flag&FLAG_SKIP_METADATA) != 0;
			int count = readVarInt(that.maxFileCount);
			QZEntry[] files = this.files;

			if (files == null) {
				// 全部是空文件
				files = new QZEntry[count];
				for (int i = 0; i < count; i++)
					files[i] = skipMetadata ? new QZEntry() : new QZEntryA();
			} else if (count != files.length) {
				if (count < files.length) fatalError();

				// 有一些空文件
				files = new QZEntry[count];
				System.arraycopy(this.files, 0, files, 0, this.files.length);
				for (int i = this.files.length; i < count; i++) files[i] = skipMetadata ? new QZEntry() : new QZEntryA();
			}

			MyBitSet empty = null, emptyFile = null;
			MyBitSet anti = null;

			int maybeInvalid = 0;
			int pos = buf.rIndex;
			while (true) {
				int id = buf.readUnsignedByte();
				if (id == 0) break;

				int end = readVarInt()+buf.rIndex;
				switch (id) {
					case kEmptyStream: empty = MyBitSet.readBits(buf, count); break;
					case kEmptyFile: emptyFile = MyBitSet.readBits(buf, Objects.requireNonNull(empty, "属性顺序错误").size()); break;
					case kAnti: anti = MyBitSet.readBits(buf, Objects.requireNonNull(empty, "属性顺序错误").size()); break;
					default: if (empty == null) maybeInvalid = id; buf.rIndex = end; break;
				}
				if (buf.rIndex != end) error("属性长度错误");
			}

			// 重排序空文件
			if (empty != null) {
				if (maybeInvalid != 0) new IOException("属性顺序错误, 在kEmpty之前遇到了"+maybeInvalid).printStackTrace();

				int emptyNo = this.files == null ? 0 : this.files.length, fileNo = 0;
				int emptyNoNo = 0;
				for (int i = 0; i < count; i++) {
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
			}

			buf.rIndex = pos;
			while (true) {
				int id = buf.readUnsignedByte();
				if (id == 0) break;

				int len = readVarInt();
				switch (id) {
					case kName -> {
						if (buf.readByte() != 0) error("iFileName.external");

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

						if (buf.rIndex != end || j != count) error("文件名太少");
					}
					case kCTime, kATime, kMTime -> {
						if (skipMetadata) {buf.rIndex += len;continue;}

						MyBitSet set = readBitsOrTrue(count);
						if (buf.readByte() != 0) error("i_Time.external");

						long off = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[id-kCTime];
						int flag = 1 << (id-kCTime);

						for (var itr = set.iterator(); itr.hasNext(); ) {
							var entry = files[itr.nextInt()];
							entry.flag |= flag;
							u.putLong(entry, off, buf.readLongLE());
						}
					}
					case kWinAttributes -> {
						if (skipMetadata) {buf.rIndex += len;continue;}

						MyBitSet set = readBitsOrTrue(count);
						if (buf.readByte() != 0) error("iAttribute.external");

						long off = QZEntryA.SPARSE_ATTRIBUTE_OFFSET[3];
						for (var itr = set.iterator(); itr.hasNext(); ) {
							var entry = files[itr.nextInt()];
							entry.flag |= QZEntry.ATTR;
							u.putInt(entry, off, buf.readIntLE());
						}
					}
					default -> buf.rIndex += len;
				}
			}

			this.files = files;
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
			ByteList b = buf;
			if (b.capacity() < len) {
				if (b.capacity() > 0) BufferPool.reserve(b);
				buf = ByteList.EMPTY;
				b = buf = (ByteList) BufferPool.buffer(false, len);
			} else {
				b.clear();
			}
			that.r.readFully(b.list, b.arrayOffset(), len);
			b.wIndex(len);
			return b;
		}
		private MyBitSet readBitsOrTrue(int size) {
			MyBitSet set;
			// all true
			if (buf.readByte() != 0) {
				set = new MyBitSet();
				set.fill(size);
			} else {
				set = MyBitSet.readBits(buf, size);
			}
			return set;
		}
		private long readVarLong() throws IOException {
			long i = buf.readVULong();
			if (i < 0) error("溢出:"+i);
			return i;
		}
		private int readVarInt() throws IOException {
			long i = buf.readVULong();
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
	public MyHashMap<String, QZEntry> getEntries() {
		if (byName.isEmpty()) {
			byName.ensureCapacity(entries.length);
			for (QZEntry entry : entries) {
				if (byName.put(entry.name, entry) != null) {
					throw new IllegalArgumentException("文件名重复！该文件可能已损坏，请通过entries()获取Entry并读取");
				}
			}
		}
		return byName;
	}

	@Override
	public List<QZEntry> entries() { return Arrays.asList(entries); }
	public QZEntry[] getEntriesByPresentOrder() { return entries; }

	public AtomicInteger parallelDecompress(TaskHandler th, BiConsumer<QZEntry, InputStream> callback) {return parallelDecompress(th, callback, password);}
	public AtomicInteger parallelDecompress(TaskHandler th, BiConsumer<QZEntry, InputStream> callback, byte[] pass) {
		var num = new AtomicInteger();

		if (blocks == null) return num;

		num.set(blocks.length);
		for (WordBlock b : blocks) {
			th.submit(() -> {
				if (r == null) throw new AsynchronousCloseException();

				try (InputStream in = getSolidStream(b, pass, false)) {
					// noinspection all
					LimitInputStream lin = new LimitInputStream(in, 0, false);
					QZEntry entry = b.firstEntry;
					long toSkip = 0;
					do {
						lin.remain = entry.uSize;

						InputStream fin = lin;
						if ((entry.flag&QZEntry.CRC) != 0) fin = new CRC32InputStream(fin, entry.crc32);
						if (toSkip > 0) fin = new SkipInputStream(fin, in, toSkip);

						callback.accept(entry, fin);
						if (fin instanceof SkipInputStream x) toSkip = x.toSkip;
						toSkip += lin.remain;

						entry = entry.next;
					} while (entry != null);
				} finally {
					if (num.decrementAndGet() == 0) {
						synchronized (num) {
							num.notifyAll();
						}
					}
				}
			});
		}

		return num;
	}
	public static void awaitParallelComplete(AtomicInteger r1) throws InterruptedException {
		while (r1.get() != 0) {
			synchronized (r1) {r1.wait();}
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
			in = new SourceInputStream.Shared(src, b.size, that, FPREAD_OFFSET);

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
			streams[0] = new SourceInputStream.Shared(src, b.size, that, FPREAD_OFFSET);
			off += b.size;

			for (int i = 0; i < sizes.length;) {
				src = src.threadSafeCopy();
				src.seek(off);

				long len = sizes[i++];
				// noinspection all
				streams[i] = new SourceInputStream(src, len);
				off += len;
			}

			var ins = node.getInputStream(b.outSizes, streams, new MyHashMap<>(), pass, limit);
			if (ins.length != 1) throw new CorruptedInputException("root node has many outputs");
			in = ins[0];
		}

		// 这个可以清理掉，因为QZEntry自身有CRC32了
		// 不过我喜欢搞骚操作，也许直接读WordBlock了
		if (verify && !isRecovering() && (b.hasCrc&1) != 0) in = new CRC32InputStream(in, b.crc);

		return in;
	}

	public synchronized QZReader parallel() {
		if (asyncReaders.isEmpty()) asyncReaders = new SimpleList<>();
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
			if (reader.fpRead != null)
				reader.fpRead.close();
			reader.closeSolidStream();
		}
		asyncReaders.clear();

		if (r != null) {
			Source r1 = r;
			r1.close();
			r = null;

			Source s = (Source) u.getAndSetObject(this, FPREAD_OFFSET, r1);
			if (s != null) s.close();

			if (password != null) Arrays.fill(password, (byte) 0);
		}
	}
}