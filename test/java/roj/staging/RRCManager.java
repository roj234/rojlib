package roj.staging;

import roj.collect.Hasher;
import roj.collect.IntMap;
import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.crypt.CRC32;
import roj.crypt.ReedSolomonECC;
import roj.io.LimitInputStream;
import roj.io.BufferPool;
import roj.io.source.FileSource;
import roj.io.source.ByteSource;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Recursive Redundancy Code
 * @author Roj234
 * @since 2025/06/04 03:42
 */
public class RRCManager {
	Source input;
	Source output;

	ReedSolomonECC recc;
	int repetitionSize = 64;
	int repetitions = 32;

	public RRCManager() {}

	public static void main(String[] args) throws IOException {
		var rrc = new RRCManager();
		//FileSource in = new FileSource("test.bin");
		FileSource out = new FileSource("text.bin.rrc");
		//rrc.encode(in, out, in.length(), 0.05f);
		rrc.decode(out);
	}

	public void encode(Source input, Source output, long dataLength, float corruptionRatio) throws IOException {
		this.input = input;
		this.output = output;
		output.put(input, 0, input.length());

		int lastDataByte = -1;
		int lastEccByte = -1;
		float lastDelta = 1;
		float lastRatio = -1;

		for (int dataBytes = 253; dataBytes > 0; dataBytes--) {
			for (int eccBytes = 2; dataBytes+eccBytes <= 255; eccBytes += 2) {
				float ratio = (eccBytes/2f) / (dataBytes+eccBytes);
				float delta = ratio - corruptionRatio;
				if (delta > 0 && delta < 0.005 && delta < lastDelta) {
					lastDelta = delta;
					lastRatio = ratio;
					lastDataByte = dataBytes;
					lastEccByte = eccBytes;
				}
			}
		}

		recc = new ReedSolomonECC(lastDataByte, lastEccByte);

		long dataOffset = 0;
		boolean isLastBlock = true;
		while (dataLength > repetitionSize) {
			long dataLengthWithECC = encodeBlock(dataOffset, dataLength, isLastBlock);
			dataOffset += dataLengthWithECC;
			dataLength = metadataLength;
			isLastBlock = false;
			System.out.println(dataLength);
		}
		encodeRepetition(dataOffset, (int) dataLength);
	}

	private long metadataLength;
	private long encodeBlock(long dataOffset, long dataLength, boolean isLastBlock) throws IOException {
		int blockSize = (int) Math.min((dataLength+recc.dataSize()-1) / recc.dataSize(), (Math.min(dataLength, 1048576) / recc.maxError()));

		input.seek(dataOffset);

		try (var in = new LimitInputStream(input.asInputStream(), dataLength, false);
			 var bar = new EasyProgressBar("生成纠错码")) {

			bar.setTotal(dataLength);
			output.seek(dataOffset+dataLength);

			recc.generateInterleavedCode(in, output, blockSize, bar);

			bar.end("生成完毕");
			long dataLengthWithECC = output.position() - dataOffset;

			output.seek(dataOffset);
			var lin = new LimitInputStream(output.asInputStream(), dataLengthWithECC, false);

			byte[] block = new byte[blockSize * recc.dataSize()];
			bar.setTitle("生成定位码");
			bar.setTotal((dataLengthWithECC + block.length - 1) / block.length);

			ByteList buf = new ByteList();
			while (true) {
				int read = lin.read(block);
				if (read < 0) break;

				// 不足补0
				for (int i = read; i < block.length; i++) block[i] = 0;

				// 用于快速识别
				int hash = polynomialRollingHash(block.length, 809, block);
				// 用于最终确定
				int crc = CRC32.crc32(block);

				buf.putInt(hash).putInt(crc);
				if (buf.readableBytes() > 1024) {
					buf.writeToStream(output);
					buf.clear();
				}

				bar.increment(1);
			}

			// 元数据
			int pos = buf.wIndex();
			buf.put(recc.dataSize())
				.put(recc.eccSize())
				.putVUInt(blockSize)
				.putVULong(dataLength)
				.putVULong(dataLengthWithECC - dataLength)
				.putBool(isLastBlock)
				.put(buf.wIndex() - pos) // 数据长度
				.writeToStream(output);

			bar.end("生成完毕");

			metadataLength = output.position() - dataLengthWithECC;
			return dataLengthWithECC;
		}
	}

	/**
	 * 多项式滚动哈希函数
	 * @param window 窗口大小
	 * @param base 基数，好像没啥要求，不过基于实测（只测试了质数），19 101 809都是不错的值
	 * @return max(0, str.length - window) + 1个哈希值
	 */
	private static int polynomialRollingHash(int window, int base, byte[] data) {
		// 计算初始哈希值
		int hash = 0;
		for (int i = 0; i < window; i++) {
			hash = (hash * base + (data[i] & 0xFF));
		}
		return hash;
	}

	private void encodeRepetition(long dataOffset, int dataLength) throws IOException {
		output.seek(dataOffset);
		byte[] data = new byte[dataLength + 5]; // 4字节CRC + 1字节长度
		output.readFully(data, 0, dataLength);
		int crc = CRC32.crc32(data, 0, dataLength);
		DynByteBuf.wrap(data).putInt(dataLength, crc).put(dataLength+4, dataLength);

		// 随后重复repetitions-1次
		output.write(data, dataLength, 5);
		for (int i = 0; i < repetitions - 1; i++) {
			output.write(data);
		}
	}

	public void decode(Source input) throws IOException {
		this.input = input;

		// 解码是编码的逆过程，首先读取重复编码，得到最后一层的滚动哈希
		DynByteBuf buf = DynByteBuf.wrap(decodeRepetition(input.length()));
		Source temp = new ByteSource();

		minBlockPos = input.length();

		while (!decodeLayer(buf, temp, minBlockPos)) {
			BufferPool.reserve(buf);
			buf = temp.buffer();
			temp = new ByteSource();
		}

		System.out.println("FileData for example: "+temp.buffer().dump());
	}

	private static final class BlockDef {
		final int crc32, index;

		BlockDef(int crc32, int index) {
			this.crc32 = crc32;
			this.index = index;
		}
	}

	private boolean decodeLayer(DynByteBuf metadata, Source output, long previousPosition) throws IOException {
		// 解码元数据
		int metadataStart = metadata.wIndex() - metadata.getU(metadata.wIndex() - 1) - 1;
		metadata.rIndex = metadataStart;

		int dataSize = metadata.readUnsignedByte();
		int eccSize = metadata.readUnsignedByte();
		int eccBlockSize = metadata.readVUInt();
		long dataLength = metadata.readVULong();
		long eccLength = metadata.readVULong();
		boolean isLastBlock = metadata.readBoolean();

		recc = new ReedSolomonECC(dataSize, eccSize);

		int positioningBlockSize = eccBlockSize * recc.dataSize();
		int positioningBlockCount = (int) ((dataLength + eccLength + positioningBlockSize - 1) / positioningBlockSize);

		metadata.rIndex = metadataStart - positioningBlockCount * 8;

		var blockHash = new IntMap<List<BlockDef>>();
		for (int i = 0; i < positioningBlockCount; i++) {
			blockHash.computeIfAbsentI(metadata.readInt(), x -> new ArrayList<>()).add(new BlockDef(metadata.readInt(), i));
		}

		// 解码受ECC保护的上层元数据
		long length = dataLength + eccLength;
		// 从0开始检测插入太费时了，三倍数据长度吧.
		int errorFixed = decodeData(output, Math.max(0, previousPosition - length * 3), length, blockHash, eccBlockSize, dataLength, eccLength);
		System.out.println("ECC检测并纠正了"+errorFixed+"个错误");

		return isLastBlock;
	}

	private long minBlockPos;
	private int decodeData(Source temp, long offset, long length, IntMap<List<BlockDef>> blockHash, int eccBlockSize, long dataLength, long eccLength) throws IOException {
		int base = 809;
		int window = eccBlockSize * recc.dataSize();
		int windowPower = MathUtils.pow(base, window-1);

		byte[] buf = ArrayCache.getByteArray(window * 2, true);
		int hash = 0;

		try {
			temp.setLength(dataLength + eccLength);

			length += offset;
			input.seek(offset);

			// 1. prepare window
			input.read(buf, 0, window);
			for (int i = 0; i < window; i++) {
				hash = (hash * base + (buf[i] & 0xFF));
			}
			offset += window;

			// 2. process stream
			while (true) {
				int remain = (int) MathUtils.clamp(length - offset, 0, window);
				input.read(buf, window, remain);
				for (int i = window + remain; i < window*2; i++) buf[i] = 0;

				for (int i = 0; i < window; i++) {
					List<BlockDef> candidates = blockHash.get(hash);
					if (candidates != null) {
						int crc = CRC32.crc32(buf, i, window);
						for (BlockDef block : candidates) {
							if (crc == block.crc32) {
								long offset1 = offset-window + i;
								if (minBlockPos > offset1) minBlockPos = offset1;

								temp.seek((long) window * block.index);
								temp.write(buf, i, window);
								break;
							}
						}
					}

					// 移除窗口中第一个字符对哈希的贡献
					hash -= windowPower * (buf[i] & 0xFF);
					// 加上新的字符
					hash = (hash * base + (buf[i + window] & 0xFF));
				}

				// 会多循环一次，并且全部0填充，这是有意的
				if (offset > length) break;
				offset += window;

				System.arraycopy(buf, window, buf, 0, window);
			}

			temp.seek(0);

			int errorFixed = recc.interleavedErrorCorrection(temp, dataLength, eccBlockSize, null);

			temp.setLength(dataLength);

			return errorFixed;
		} finally {
			ArrayCache.putArray(buf);
		}
	}

	private byte[] decodeRepetition(long position) throws IOException {
		byte[] repetitionCode = new byte[256 * repetitions];
		input.seek(Math.max(0, position - repetitionCode.length));
		int len = input.read(repetitionCode);

		ToIntMap<byte[]> candidates = new ToIntMap<>();
		candidates.setHasher(Hasher.array(byte[].class));

		byte[] tmp = new byte[256];
		ByteList buf = DynByteBuf.wrap(repetitionCode);
		buf.wIndex(len);

		for (int i = len - 1; i >= 6; i--) {
			int dataLength = buf.getU(i);
			if (dataLength+4 > i) continue;
			int crc = buf.readInt(i - 4);
			buf.readFully(i - 4 - dataLength, tmp, 0, dataLength);

			if (CRC32.crc32(tmp, 0, dataLength) == crc) {
				int count = candidates.increment(Arrays.copyOf(tmp, dataLength), 1);
			}
		}

		int maxCount = 0;
		tmp = null;

		for (ToIntMap.Entry<byte[]> entry : candidates.selfEntrySet()) {
			if (entry.value > maxCount) {
				maxCount = entry.value;
				tmp = entry.getKey();
			}
		}

		return tmp;
	}
}
