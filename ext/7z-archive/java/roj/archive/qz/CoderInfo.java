package roj.archive.qz;

import org.jetbrains.annotations.NotNull;
import roj.collect.HashMap;
import roj.collect.IntMap;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 注：Javadoc由AI给我优化了，过了几年我忘得差不多了，AI更加不懂了，所以要是你也看不懂，好吧，没办法
 * @author Roj234
 * @since 2023/5/30 17:50
 */
final class CoderInfo extends QZCoder {
	private final QZCoder coder;
	final Object[] uses;
	final int provides;
	/**
	 * 输出流索引，用于跟踪编码器的输出位置，同时是该编码器数据块大小在WordBlock的输入输出大小数组中的索引
	 * -1表示该编码器不直接参与最终输出流的写入。
	 */
	private int outputStreamIndex;

	CoderInfo(QZCoder coder, int outputStreamIndex) {
		this.coder = coder;
		if (coder instanceof QZComplexCoder complexCoder) {
			this.uses = new Object[complexCoder.useCount()];
			this.provides = complexCoder.provideCount();
		} else {
			this.uses = new Object[1];
			this.provides = 1;
		}
		this.outputStreamIndex = outputStreamIndex;
	}

	@Override byte[] id() {return null;}
	@Override public InputStream decode(InputStream a, byte[] b, long c, AtomicInteger d) throws IOException {throw new IllegalArgumentException("CoderInfo应该由根节点解压，而不是QZCoder，这是编程错误。");}
	@Override public OutputStream encode(OutputStream a) throws IOException {throw new IllegalArgumentException("CoderInfo应该由根节点压缩，而不是QZCoder，这是编程错误。");}

	@Override
	public String toString() { return String.valueOf(outputStreamIndex)+'#'+ coder; }

	/**
	 * Checks if this coder or any of its dependencies is of the specified type.
	 *
	 * @param type The coder type to check for
	 * @return true if this coder or any dependency matches the type
	 */
	public boolean hasProcessor(Class<? extends QZCoder> type) {
		if (type.isInstance(coder)) return true;
		for (Object connection : uses) {
			if (connection.getClass() == Integer.class) {
				continue;
			} else if (connection.getClass() == CoderInfo.class) {
				if (((CoderInfo) connection).hasProcessor(type)) return true;
			} else {
				IntMap.Entry<CoderInfo> entry = Helpers.cast(connection);
				if (entry.getValue().hasProcessor(type)) return true;
			}
		}
		return false;
	}

	/**
	 * Creates input streams for decoding, handling complex coder dependencies.
	 *
	 * @param outputSizes Array of output sizes for each stream
	 * @param fileStreams Input streams from files
	 * @param streamCache Cache for already created streams
	 * @param password Password for decryption
	 * @param memoryLimit Memory limit for decompression
	 * @return Array of input streams for this coder
	 * @throws IOException If an I/O error occurs
	 */
	InputStream[] getInputStream(long[] outputSizes, InputStream[] fileStreams,
								 Map<CoderInfo, InputStream[]> streamCache,
								 byte[] password, AtomicInteger memoryLimit) throws IOException {
		var assoc = new InputStream[uses.length];
		for (int i = 0; i < uses.length; i++) {
			Object connection = uses[i];
			if (connection.getClass() == Integer.class) {
				assoc[i] = fileStreams[(int) connection];
			} else if (connection.getClass() == CoderInfo.class) {
				var cached = streamCache.get(connection);
				if (cached == null) {
					cached = ((CoderInfo) connection).getInputStream(outputSizes, fileStreams, streamCache, password, memoryLimit);
					streamCache.put((CoderInfo) connection, cached);
				}
				assoc[i] = cached[0];
			} else {
				IntMap.Entry<CoderInfo> entry = Helpers.cast(connection);
				var cached = streamCache.get(entry.getValue());
				if (cached == null) {
					cached = entry.getValue().getInputStream(outputSizes, fileStreams, streamCache, password, memoryLimit);
					streamCache.put(entry.getValue(), cached);
				}
				assoc[i] = cached[entry.getIntKey()];
			}
		}

		return coder instanceof QZComplexCoder complexCoder
				? complexCoder.complexDecode(assoc, outputSizes, outputStreamIndex, memoryLimit)
				: new InputStream[] {coder.decode(assoc[0], password, outputSizes[outputStreamIndex], memoryLimit)};
	}

	/**
	 * 创建用于编码的组合输出流。
	 * <p>
	 * 该方法会构建一个包含主输出流和额外统计流的组合流，
	 * 通过{@link SizeTracker}自动统计各数据块的大小。
	 *
	 * @param block 要编码的单词块，用于记录压缩前后的大小信息
	 * @param output 主输出流，最终数据将写入此流
	 * @return 组合输出流，负责协调多个子流的写入操作
	 * @throws IOException 当流创建失败时抛出
	 */
	OutputStream getOutputStream(WordBlock block, OutputStream output) throws IOException {
		OutputStream[] streams = new OutputStream[block.extraSizes.length+1];
		// noinspection all
		streams[0] = new SizeTracker(block, output);
		for (int i = 1; i < streams.length; i++)
			// noinspection all
			streams[i] = new SizeTracker(block, i);

		return new CompositeOutputStream(createOutputStreams(block, streams, new HashMap<>())[0], streams);
	}
	private OutputStream[] createOutputStreams(WordBlock block, OutputStream[] fileStreams,
											   Map<CoderInfo, OutputStream[]> streamCache) throws IOException {
		var assoc = new OutputStream[uses.length];
		for (int i = 0; i < uses.length; i++) {
			Object connection = uses[i];
			if (connection.getClass() == Integer.class) {
				assoc[i] = fileStreams[(int) connection];
			} else if (connection.getClass() == CoderInfo.class) {
				var cached = streamCache.get(connection);
				if (cached == null) {
					cached = ((CoderInfo) connection).createOutputStreams(block, fileStreams, streamCache);
					streamCache.put((CoderInfo) connection, cached);
				}
				assoc[i] = cached[0];
			} else {
				IntMap.Entry<CoderInfo> entry = Helpers.cast(connection);
				var cached = streamCache.get(entry.getValue());
				if (cached == null) {
					cached = entry.getValue().createOutputStreams(block, fileStreams, streamCache);
					streamCache.put(entry.getValue(), cached);
				}
				assoc[i] = cached[entry.getIntKey()];
			}
		}

		var resultStreams = coder instanceof QZComplexCoder complexCoder
				? complexCoder.complexEncode(assoc)
				: new OutputStream[] {coder.encode(assoc[0])};

		// 输入(uncompressed size | partial compressed size)大小计数器
		if (outputStreamIndex >= 0) {
			for (int i = 0; i < resultStreams.length; i++) {
				// noinspection all
				resultStreams[i] = block.new Counter(resultStreams[i], outputStreamIndex+i);
			}
		}
		return resultStreams;
	}

	/**
	 * 统计WordBlock中各个文件区域大小(final compressed size)的辅助输出流。
	 * <p>
	 * 根据构造模式不同，可能直接写入文件或缓存数据：
	 * <ul>
	 *   <li>sizeIndex >= 0时：缓存数据到内存，统计额外流的大小</li>
	 *   <li>sizeIndex = -1时：直接写入文件流，统计主数据大小</li>
	 * </ul>
	 */
	static final class SizeTracker extends OutputStream {
		final WordBlock owner;
		final OutputStream fileStream;
		private final ByteList buffer;
		private final int sizeIndex;

		// memory cache
		SizeTracker(WordBlock owner, int sizeIndex) {
			this.owner = owner;
			this.sizeIndex = sizeIndex-1;
			this.fileStream = null;
			this.buffer = ByteList.allocate(1024);
		}

		// direct to file
		SizeTracker(WordBlock owner, OutputStream fileStream) {
			this.owner = owner;
			this.sizeIndex = -1;
			this.fileStream = fileStream;
			this.buffer = null;
		}

		@Override
		public void write(int b) throws IOException {
			if (fileStream != null) fileStream.write(b);
			else buffer.put(b);

			if (sizeIndex >= 0) owner.extraSizes[sizeIndex]++;
			else owner.size++;
		}

		@Override
		public void write(@NotNull byte[] b, int off, int len) throws IOException {
			ArrayUtil.checkRange(b, off, len);
			if (len == 0) return;

			if (fileStream != null) fileStream.write(b, off, len);
			else buffer.put(b, off, len);

			if (sizeIndex >= 0) owner.extraSizes[sizeIndex] += len;
			else owner.size += len;
		}
	}

	/**
	 * 组合输出流，协调多个{@link SizeTracker}的写入操作。
	 * <p>
	 * 关闭时会自动将缓存数据刷入主文件流，并确保所有Finishable流正确完成。
	 */
	static final class CompositeOutputStream extends OutputStream implements Finishable {
		private final OutputStream primaryOutput;
		private final OutputStream[] fileStreams;

		public CompositeOutputStream(OutputStream primaryOutput, OutputStream[] fileStreams) {
			this.primaryOutput = primaryOutput;
			this.fileStreams = fileStreams;
		}

		public void write(int b) throws IOException { primaryOutput.write(b); }
		public void write(@NotNull byte[] b, int off, int len) throws IOException { primaryOutput.write(b, off, len); }
		public void flush() throws IOException { primaryOutput.flush(); }
		public void finish() throws IOException { if (primaryOutput instanceof Finishable f) f.finish(); }

		public void close() throws IOException {
			Exception e1 = null;
			try {
				primaryOutput.close();
			} catch (Exception e) {
				e1 = e;
			}

			OutputStream fileStream = ((SizeTracker) fileStreams[0]).fileStream;
			// 真正写入文件
			for (int i = 1; i < fileStreams.length; i++) {
				ByteList buffer = ((SizeTracker) fileStreams[i]).buffer;
				buffer.writeToStream(fileStream);
				buffer.release();
			}

			if (e1 != null) Helpers.athrow(e1);
		}
	}

	/**
	 * 将当前编码器的指定输入连接到另一个编码器的输出。
	 *
	 * @param streamId 当前编码器的输入流索引（0 ≤ index < uses.length）
	 * @param provider 提供输出流的源编码器（非null）
	 * @param providerStreamId 源编码器的输出流索引（0 ≤ index < provider.provides）
	 */
	void pipe(int streamId, CoderInfo provider, int providerStreamId) {
		uses[streamId] = providerStreamId == 0 ? provider : new IntMap.Entry<>(providerStreamId, provider);
	}

	/**
	 * 将当前编码器的指定输入连接到文件块的原始流。
	 *
	 * @param blockId 文件块索引（对应fileStreams数组的索引）
	 * @param streamId 当前编码器的输入流索引（0 ≤ index < uses.length）
	 */
	void setFileInput(int blockId, int streamId) {
		uses[streamId] = blockId;
	}

	/**
	 * Writes coder information to the output buffer.
	 *
	 * @param block The word block being processed
	 * @param buffer The output buffer
	 */
	void writeCoders(WordBlock block, ByteList buffer) {
		ByteList options = IOUtil.getSharedByteBuf();

		var sortedCoders = (CoderInfo[]) block.coder;
		buffer.putVUInt(sortedCoders.length);

		// Write coders
		for (CoderInfo coder : sortedCoders) {
			options.clear();
			coder.coder.writeOptions(options);

			byte[] id = coder.coder.id();

			int flags = id.length;
			if (coder.coder instanceof QZComplexCoder) flags |= 0x10;
			if (options.wIndex() > 0) flags |= 0x20;
			buffer.put(flags).put(id);

			if (coder.coder instanceof QZComplexCoder) {
				buffer.putVUInt(coder.uses.length).putVUInt(coder.provides);
			}

			if (options.wIndex() > 0) buffer.putVUInt(options.wIndex()).put(options);
		}

		// Write pipes
		int used = 0;
		int[] fileStreamIds = new int[block.extraSizes.length+1];

		for (var coder : sortedCoders) {
			Object[] uses = coder.uses;
			for (int j = 0; j < uses.length; j++) {
				Object pipe = uses[j];
				if (pipe.getClass() == CoderInfo.class) {
					buffer.putVUInt(j+used)
						  .putVUInt(findOutputIndex(sortedCoders, (CoderInfo) pipe));
				} else if (pipe.getClass() != Integer.class) {
					IntMap.Entry<CoderInfo> entry = Helpers.cast(pipe);
					buffer.putVUInt(j+used)
						  .putVUInt(findOutputIndex(sortedCoders, entry.getValue()) + entry.getIntKey());
				} else {
					fileStreamIds[(int)pipe] = j+used;
				}
			}
			used += uses.length;
		}

		// Write fileStream mapping
		for (int id : fileStreamIds) buffer.putVUInt(id);

		if (block.outSizes.length == 0) return;

		// 最外面OutputStream写的是uSize,但是实际的streamId是0,需要交换一下
		long uncompressedSize = block.uSize;
		long compressedSize = block.outSizes[block.outSizes.length-1];
		System.arraycopy(block.outSizes, 0, block.outSizes, 1, block.outSizes.length-1);
		block.outSizes[0] = uncompressedSize;
		block.uSize = compressedSize;
	}
	private static int findOutputIndex(CoderInfo[] coders, CoderInfo target) {
		int index = 0;
		for (CoderInfo coder : coders) {
			if (coder == target) break;
			index += coder.provides;
		}
		return index;
	}

	/**
	 * 重置输出流索引并返回原始值，用于最终大小计算。
	 * <p>
	 * 该方法会将当前编码器的outputStreamIndex标记为无效（-1），
	 * 并调整后续编码器的索引以保证连续性。
	 *
	 * @param block 包含编码器排序信息的单词块
	 * @return 原始输出流索引，对应主未压缩数据大小的存储位置
	 */
	int getUncompressedSizeIndex(WordBlock block) {
		assert outputStreamIndex >= 0;
		int originalIndex = outputStreamIndex;
		outputStreamIndex = -1;

		var sortedCoders = (CoderInfo[]) block.coder;
		for (CoderInfo coder : sortedCoders) {
			if (coder.outputStreamIndex > originalIndex) coder.outputStreamIndex--;
		}

		return originalIndex;
	}
}