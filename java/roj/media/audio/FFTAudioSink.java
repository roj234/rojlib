package roj.media.audio;

import roj.math.MathUtils;
import roj.math.Vec2d;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

import static roj.media.audio.FFT.*;

/**
 * @author Roj234-N
 * @since 2025/5/12 5:06
 */
@Deprecated
public abstract class FFTAudioSink implements AudioSink {
	// 调整特定频率范围的强度
	public static Vec2d[] applyGain(Vec2d[] fftResult, double sampleRate, double startFreq, double endFreq, double startGain, double endGain) {
		int n = fftResult.length;
		// 限制索引在有效范围 [0, n/2]
		int startIndex = Math.max(0, (int) (startFreq * n / sampleRate));
		int endIndex = Math.min(n/2, (int) (endFreq * n / sampleRate));

		var sigmoidSequence = MathUtils.createSigmoidSequence(
				startGain,
				endGain,
				endIndex - startIndex
		);

		for (int i = startIndex; i < endIndex; i++) {
			double gain = sigmoidSequence.nextDouble();
			// 应用增益到当前频率及其镜像
			fftResult[i].mul(gain);
			if (i > 0 && i < n/2) { // 避免重复处理Nyquist频率
				fftResult[n - i - 1].mul(gain);
			}
		}

		return fftResult;
	}

	private final AudioSink sink;

	short[][] channelBuffer;
	Vec2d[] fftBuffer;
	int channelPos;

	int fftSize;                      // FFT窗口大小
	int overlap;               // 50%重叠
	double[] hammingWindow;                   // 汉明窗
	short[][] overlapBuffer;                  // 重叠缓冲区（每个通道独立）

	protected FFTAudioSink(AudioSink sink, int fftSize) {
		this.sink = sink;
		// 初始化FFT相关参数
		this.fftSize = fftSize;
		overlap = fftSize / 2;

		// 初始化汉明窗
		hammingWindow = new double[fftSize];
		for (int i = 0; i < fftSize; i++) {
			hammingWindow[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (fftSize - 1));
		}
	}

	@Override public void open(AudioFormat format) throws IOException {
		sink.open(format);
		channelBuffer = new short[format.getChannels()][fftSize];
		fftBuffer = newComplexArray(fftSize);
		channelPos = 0;

		// 初始化重叠缓冲区（每个通道）
		overlapBuffer = new short[format.getChannels()][overlap];
	}

	@Override public void close() {
		sink.close();}

	@Override
	public void write(byte[] b, int off, int len) {
		ByteList data = DynByteBuf.wrap(b, off, len);
		for (int i = 0; i < len; i += channelBuffer.length * 2) {
			int pos = channelPos++;
			for (int channel = 0; channel < channelBuffer.length; channel++) {
				channelBuffer[channel][pos] = (short) data.readUShortLE();
			}

			if (pos == fftBuffer.length-1) flushBuffer();
		}
	}

	@Override
	public void flush() {
		flushBuffer();
	}
	private void flushBuffer() {
		Vec2d[] complex = FFT.pcmToComplex(channelBuffer[0], fftBuffer);

		// ==== 1. 应用汉明窗到当前帧 ====
		for (int i = 0; i < fftSize; i++) {
			//complex[i].mul(hammingWindow[i]);
		}

		// ==== 2. 执行FFT处理 ====
		fft(complex);

		process(complex);

		ifft(complex);

		// ==== 3. 重叠相加处理 ====
		short[] currentFrame = FFT.complexToPcm(complex, channelBuffer[0]);
		short[] prevOverlap = overlapBuffer[0];

		// 合并重叠区与新数据
		for (int j = 0; j < overlap; j++) {
			currentFrame[j] += prevOverlap[j] * hammingWindow[j];
		}

		var out = new ByteList();
		for (int j = overlap; j < channelPos; j++) {
			for (int i = 0; i < channelBuffer.length; i++) {
				out.putShortLE(channelBuffer[i][j]);
			}
		}

		// 保存当前帧的后 overlap 样本到重叠缓冲区
		System.arraycopy(currentFrame, overlap, prevOverlap, 0, overlap);

		// ==== 4. 输出处理后的数据 ====
		sink.write(out.list, 0, out.wIndex());

		// ==== 5. 滑动窗口：保留后 overlap 样本到下一次输入 ====
		// 将当前 channelBuffer 的后 overlap 样本移动到前部
		for (short[] buffer : channelBuffer) {
			System.arraycopy(buffer, overlap, buffer, 0, overlap);
		}
		channelPos = overlap; // 下一次从 overlap 位置开始填充
	}

	protected abstract void process(Vec2d[] complex);
}
