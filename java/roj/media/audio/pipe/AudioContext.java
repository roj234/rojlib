package roj.media.audio.pipe;

import roj.media.audio.AudioSink;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;

/**
 * @author Roj234
 * @since 2023/2/3 14:49
 */
public class AudioContext implements AudioSink {
	private AudioFormat format;
	private int frameSize;
	private boolean bigEndian;
	private int sampleBytes;
	private int channels;
	private AudioBuffer buffer;

	public AudioContext() {}

	public void register(AudioNode pipeline) {
	}

	public void connect(AudioNode from, AudioNode to) {
	}

	public void disconnect(AudioNode from, AudioNode to) {
	}

	public void disconnectFrom(AudioNode from) {
	}

	public void disconnectTo(AudioNode to) {
	}

	@Override
	public void open(AudioFormat format) {
		this.format = format;
		this.frameSize = format.getFrameSize();
		this.bigEndian = format.isBigEndian();
		this.sampleBytes = format.getSampleSizeInBits() / 8;
		this.channels = format.getChannels();
	}

	@Override
	public void write(byte[] buf, int off, int len) {
		AudioBuffer ab = convert(DynByteBuf.wrap(buf, off, len), len / sampleBytes / channels);
		// TODO
	}

	public AudioBuffer convert(DynByteBuf DynByteBuf, int samplesPerChannel) {
		if (buffer == null || buffer.immutable || buffer.samples < samplesPerChannel)
			buffer = createBuffer(samplesPerChannel);
		// 创建目标AudioBuffer
		AudioBuffer audioBuffer = buffer;

		// 根据编码类型进行转换
		switch (format.getEncoding().toString()) {
			case "PCM_SIGNED" -> convertPcmSigned(DynByteBuf, audioBuffer, samplesPerChannel);
			case "PCM_UNSIGNED" -> convertPcmUnsigned(DynByteBuf, audioBuffer, samplesPerChannel);
			case "PCM_FLOAT" -> convertPcmFloat(DynByteBuf, audioBuffer, samplesPerChannel);
			default -> throw new UnsupportedOperationException("Unsupported encoding: " + format.getEncoding());
		}

		audioBuffer.samples = samplesPerChannel;
		return audioBuffer;
	}

	private AudioBuffer createBuffer(int samplesPerChannel) {
		AudioBuffer buffer = new AudioBuffer();
		buffer.sampleRate = (int) format.getSampleRate();
		buffer.channels = channels;
		buffer.data = new float[channels][samplesPerChannel];
		return buffer;
	}

	private void convertPcmSigned(DynByteBuf src, AudioBuffer dst, int samples) {
		for (int i = 0; i < samples; i++) {
			for (int ch = 0; ch < channels; ch++) {
				dst.data[ch][i] = readSample(src) / getNormalizationFactor();
			}
		}
	}

	private void convertPcmUnsigned(DynByteBuf src, AudioBuffer dst, int samples) {
		for (int i = 0; i < samples; i++) {
			for (int ch = 0; ch < channels; ch++) {
				long unsignedValue = readSample(src) & ((1L << (sampleBytes * 8)) - 1);
				dst.data[ch][i] = (unsignedValue - getMidpoint()) / getMidpoint();
			}
		}
	}

	private void convertPcmFloat(DynByteBuf src, AudioBuffer dst, int samples) {
		//src.order(byteOrder);
		for (int i = 0; i < samples; i++) {
			for (int ch = 0; ch < channels; ch++) {
				dst.data[ch][i] = src.readFloat();
			}
		}
	}

	private int readSample(DynByteBuf buffer) {
		return switch (sampleBytes) {
			case 1 -> buffer.readByte();
			case 2 -> bigEndian ? buffer.readShort() : buffer.readUShortLE();
			case 3 -> bigEndian ? buffer.readMedium() : buffer.readMediumLE();
			case 4 -> bigEndian ? buffer.readInt() : buffer.readIntLE();
			default -> throw new IllegalArgumentException("Unsupported sample size: " + sampleBytes * 8);
		};
	}

	private float getNormalizationFactor() {
		return (float) Math.pow(2, format.getSampleSizeInBits() - 1);
	}

	private float getMidpoint() {
		return (float) Math.pow(2, format.getSampleSizeInBits()) / 2;
	}
}