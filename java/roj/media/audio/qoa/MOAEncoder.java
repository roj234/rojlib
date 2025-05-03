package roj.media.audio.qoa;

import roj.crypt.CryptoFactory;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.media.audio.AudioEncoder;
import roj.media.audio.AudioMetadata;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234-N
 * @since 2025/5/13 7:51
 */
public class MOAEncoder extends MOA implements AudioEncoder {
	private final LMS test_lms = new LMS(), best_lms = new LMS();
	private byte[] prev_scalefactor;

	private Source out;
	private int syncword;
	private boolean bigEndian;

	@Override
	public void start(Source out, AudioFormat format, AudioMetadata meta) throws IOException {
		if (format.getSampleSizeInBits() != 16 || format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED)
			throw new IllegalArgumentException("Only supported 16bit Signed PCM, not "+format);

		this.out = out;
		this.channels = format.getChannels();
		this.bigEndian = format.isBigEndian();
		int samplerate = (int) format.getSampleRate();

		int _bitPerSample = 3;
		boolean _vbr = false;

		int sampleRateIndex;
		found: {
		for (int i = 0; i < SampleRates.length; i++) {
			if (SampleRates[i] == samplerate) {
				sampleRateIndex = i;
				break found;
			}
		}
		throw new IllegalStateException("Unsupported sample rate");
		}

		// 生成同步字
		int syncword =
				((_vbr?1:0)<<23) |
				(sampleRateIndex << 20) |
				(_bitPerSample << 18) |
				(channels << 14) |
				(0 << 13) |
				(1 << 11);
		this.syncword = syncword | (1<<10)/* Data flag */;

		outputBuffer.putMedium(syncword);
		int checksum = CryptoFactory.CRC8(outputBuffer, 3);
		out.writeInt((syncword << 8) | checksum);

		outputBuffer.clear();
		inputBuffer.clear();

		// 初始化LMS
		this.lms = new LMS[channels];
		for (int i = 0; i < channels; i++) lms[i] = new LMS();
		this.prev_scalefactor = new byte[channels];
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		inputBuffer.put(b, off, len);

		int maxFrameSize = SamplesPerFrame * channels * 2;
		while (inputBuffer.readableBytes() >= maxFrameSize) {
			encodeFrame(inputBuffer, SamplesPerFrame);
			inputBuffer.rIndex += maxFrameSize;

			outputBuffer.writeToStream(out);
			outputBuffer.clear();
		}

		inputBuffer.compact();
	}

	public void encodeFrame(DynByteBuf samples, int frame_len) {
		int channels = this.channels;
		int slices = (frame_len + SamplesPerSlice - 1) / SamplesPerSlice;

		// EOS标记
		if (slices < MaxSlicesPerFrame) syncword |= (1<<13) | slices;

		var ob = outputBuffer.putMedium(syncword);
		int checksum = CryptoFactory.CRC8(ob, 3);
		ob.put(checksum);

		for (int c = 0; c < channels; c++) {
			// 写入当前 LMS 状态
			for (int i = 0; i < LmsHistorySize; i++) {
				ob.putShort(lms[c].history[i]).putShort(lms[c].weights[i]);
			}
		}

		LMS test_lms = this.test_lms;
		LMS best_lms = this.best_lms;
		byte[] prev_scalefactor = this.prev_scalefactor;
		long baseAddr = samples._unsafeAddr() + samples.rIndex;

		// 编码所有样本
		for (int sliceId = 0; sliceId < frame_len; sliceId += SamplesPerSlice) {
			for (int ch = 0; ch < channels; ch++) {
				int slice_count = Math.min(SamplesPerSlice, frame_len - sliceId);
				int slice_start = sliceId * channels + ch;
				int slice_end = (sliceId + slice_count) * channels + ch;

				LMS current_lms = this.lms[ch];
				long best_rank = Long.MAX_VALUE;
				long best_slice = 0;
				int best_scalefactor = 0;

				// 测试哪个scaleFactor最好
				for (int sfId = 0; sfId < 16; sfId++) {
					System.arraycopy(current_lms.history, 0, test_lms.history, 0, LmsHistorySize);
					System.arraycopy(current_lms.weights, 0, test_lms.weights, 0, LmsHistorySize);

					int scalefactor = (sfId + prev_scalefactor[ch]) & 15;
					long slice = scalefactor;
					long current_rank = 0;

					for (int i = slice_start; i < slice_end; i += channels) {
						int sample = (short) (bigEndian
								? U.get16UB(samples.array(), baseAddr + i * 2L)
								: U.get16UL(samples.array(), baseAddr + i * 2L));

						int predicted = test_lms.predict();
						int residual = sample - predicted;
						int scaled = scaledDiv(residual, scalefactor);
						int clamped = MathUtils.clamp(scaled, -8, 8);
						int quantized = QuantizeTab[clamped + 8];
						int dequantized = DequantizeTab[scalefactor][quantized];
						int reconstructed = clampS16(predicted + dequantized);

						int weights_penalty = ((
								test_lms.weights[0] * test_lms.weights[0] +
								test_lms.weights[1] * test_lms.weights[1] +
								test_lms.weights[2] * test_lms.weights[2] +
								test_lms.weights[3] * test_lms.weights[3]
						) >> 18) - 0x8ff;

						if (weights_penalty < 0) weights_penalty = 0;

						int error = (sample - reconstructed);
						long error_sq = (long) error * error;

						// 防止LMS权重溢出
						current_rank += error_sq + (weights_penalty * weights_penalty);
						if (current_rank > best_rank) break;

						test_lms.update(reconstructed, dequantized);
						slice = (slice << 3) | quantized;
					}

					if (current_rank < best_rank) {
						best_rank = current_rank;
						best_slice = slice;
						best_scalefactor = scalefactor;
						System.arraycopy(test_lms.history, 0, best_lms.history, 0, LmsHistorySize);
						System.arraycopy(test_lms.weights, 0, best_lms.weights, 0, LmsHistorySize);
					}
				}

				prev_scalefactor[ch] = (byte) best_scalefactor;
				System.arraycopy(best_lms.history, 0, lms[ch].history, 0, LmsHistorySize);
				System.arraycopy(best_lms.weights, 0, lms[ch].weights, 0, LmsHistorySize);

				best_slice <<= (SamplesPerSlice - slice_count) * 3;
				ob.putLong(best_slice);
			}
		}
	}

	@Override
	public void finish() throws IOException {
		// 处理剩余样本
		if (!inputBuffer.isEmpty()) {
			encodeFrame(inputBuffer, inputBuffer.readableBytes() / (channels * 2));
			outputBuffer.writeToStream(out);
			outputBuffer.clear();
		}
		out = null;
		lms = null;
		prev_scalefactor = null;
	}

	@Override
	public void close() throws IOException {
		finish();
	}
}
