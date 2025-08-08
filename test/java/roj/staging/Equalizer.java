package roj.staging;

import roj.audio.AudioSink;
import roj.audio.FFT;
import roj.audio.SpeakerSink;
import roj.audio.mp3.MP3Decoder;
import roj.io.source.FileSource;
import roj.math.MathUtils;
import roj.math.Vec2d;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Roj234-N
 * @since 2025/5/12 5:06
 */
public abstract class Equalizer implements AudioSink {
	public static void main(String[] args) throws Exception {
		var mp3Decoder = new MP3Decoder();

		var speaker = new SpeakerSink();
		var sink = new Equalizer(speaker, 8192) {
			@Override
			public void open(AudioFormat format) throws IOException {
				super.open(format);

				System.out.println(mp3Decoder.getDebugInfo());
				float sampleRate = format.getSampleRate();
				applyGain(sampleRate, 0, 1000, 0, 1.5);
				applyGain(sampleRate, 1000, 24000, 0, 0);
			}

			@Override
			protected void process(Vec2d[] complex) {
				Tty.directWrite(Tty.Cursor.to(0,0));
				FFT.displaySpectrum(complex, Tty.windowWidth, Tty.windowHeight-2);
			}
		};

		mp3Decoder.open(new FileSource(args[0]), false);
		mp3Decoder.connect(sink);
		sink.flush();
		mp3Decoder.close();
	}

	// 调整特定频率范围的强度
	public void applyGain(double sampleRate, double startFreq, double endFreq, double startGain, double endGain) {
		int n = fftSize;
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
			this.gain[i] = gain;
		}
	}

	private final AudioSink sink;
	private final int fftSize;
	short[][] channelBuffer;
	Vec2d[][] fftBuffers;  // 每个声道独立的FFT缓冲区
	int channelPos;
	short[][] overlapBuffer;  // 用于重叠衔接的前一块数据
	double[] gain;

	protected Equalizer(AudioSink sink, int fftSize) {
		this.sink = sink;
		this.fftSize = fftSize;
		this.gain = new double[fftSize];
		// 默认增益设置为1.0 (无增益)
		for (int i = 0; i < fftSize; i++) {
			gain[i] = 1.0;
		}
	}

	@Override
	public void open(AudioFormat format) throws IOException {
		sink.open(format);
		int channels = format.getChannels();
		channelBuffer = new short[channels][fftSize];
		fftBuffers = new Vec2d[channels][];
		overlapBuffer = new short[channels][fftSize / 2];  // 50%重叠缓冲

		// 初始化每个声道的FFT缓冲区
		for (int i = 0; i < channels; i++) {
			fftBuffers[i] = FFT.newComplexArray(fftSize);
		}
		channelPos = 0;
	}

	@Override
	public void close() {
		sink.close();
	}

	@Override
	public void write(byte[] b, int off, int len) {
		ByteList data = DynByteBuf.wrap(b, off, len);
		int channels = channelBuffer.length;
		int samples = len / (channels * 2);

		for (int i = 0; i < samples; i++) {
			int pos = channelPos;
			for (int channel = 0; channel < channels; channel++) {
				channelBuffer[channel][pos] = (short) data.readUShortLE();
			}

			channelPos++;
			if (channelPos == fftSize) {
				flushBuffer();
				// 重叠衔接：将后半块移到前半块位置
				for (int channel = 0; channel < channels; channel++) {
					System.arraycopy(channelBuffer[channel], fftSize / 2,
							overlapBuffer[channel], 0, fftSize / 2);
				}
				channelPos = fftSize / 2;  // 保留后半块作为新块前半部分
			}
		}
	}

	private void flushBuffer() {
		int channels = channelBuffer.length;
		for (int channel = 0; channel < channels; channel++) {
			// 1. 合并重叠数据（当前块 + 前一块后半部分）
			System.arraycopy(overlapBuffer[channel], 0, channelBuffer[channel], 0, fftSize / 2);

			// 2. PCM转复数
			FFT.pcmToComplex(channelBuffer[channel], fftBuffers[channel]);

			// 3. 执行FFT
			FFT.fft(fftBuffers[channel]);

			// 4. 关键：应用对称增益补偿（保持实数信号共轭对称）
			for (int i = 0; i < fftSize / 2; i++) {
				// 自动补偿对称频率点（N-i ≡ -i）
				int conjIndex = (fftSize - i) % fftSize;
				double combinedGain = gain[i];
				fftBuffers[channel][i].x *= combinedGain;
				fftBuffers[channel][i].y *= combinedGain;
				fftBuffers[channel][conjIndex].x *= combinedGain;
				fftBuffers[channel][conjIndex].y *= combinedGain;
			}

			if (channel == 0)
				process(fftBuffers[channel]);

			// 5. IFFT并转回PCM
			FFT.ifft(fftBuffers[channel]);
			FFT.complexToPcm(fftBuffers[channel], channelBuffer[channel]);
		}

		// 7. 输出处理后的音频（跳过前50%重叠部分避免重复）
		ByteList out = new ByteList();
		int validStart = fftSize / 2;  // 取有效数据的起始位置
		for (int j = validStart; j < fftSize; j++) {
			for (int i = 0; i < channels; i++) {
				out.putShortLE(channelBuffer[i][j]);
			}
		}
		sink.write(out.list, 0, out.wIndex());
	}

	protected abstract void process(Vec2d[] complex);
}
