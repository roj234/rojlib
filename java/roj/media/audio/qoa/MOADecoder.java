package roj.media.audio.qoa;

import roj.crypt.CryptoFactory;
import roj.io.CorruptedInputException;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.media.audio.AudioDecoder;
import roj.media.audio.AudioMetadata;
import roj.media.audio.AudioSink;
import roj.reflect.ReflectionUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234-N
 * @since 2025/5/12 17:31
 */
public class MOADecoder extends MOA implements AudioDecoder {
	private Source in;
	private long sample, samples;
	private int samplerate;

	@Override
	public AudioMetadata open(Source in, boolean parseMetadata) throws IOException {
		if (getState() != READY) throw new IllegalStateException("not READY state");

		in.read(inputBuffer, 4);
		int checksum = CryptoFactory.CRC8(inputBuffer, 3);
		int syncword = inputBuffer.readMedium();
		if (checksum != inputBuffer.readUnsignedByte()) throw new CorruptedInputException("checksum error");

		//int reserved = (syncword >>> 8) & 1;
		int block = (syncword >>> 9) & 1;
		int data = (syncword >>> 10) & 1;
		int version = (syncword >>> 11) & 3;
		//int end = (syncword >>> 13) & 1;
		channels = ((syncword >>> 14) & 15);
		//int bitsPerSample = ((syncword >>> 18) & 3);
		samplerate = SampleRates[((syncword >>> 20) & 7)];
		//int vbr = (syncword >>> 23) & 1;
		if (channels == 0 || samplerate == 0 || version != 1 || data != 0)
			throw new CorruptedInputException("syncword error: "+Integer.toHexString(syncword));

		if (block != 0) {
			in.read(inputBuffer, 4);
			samples = inputBuffer.readInt();
		} else {
			samples = 0;
		}

		this.in = in;
		this.sample = 0;

		lms = new LMS[channels];
		for (int i = 0; i < channels; i++) lms[i] = new LMS();

		inputBuffer.clear();
		return null;
	}

	@Override
	public void connect(AudioSink sink) throws IOException {
		if (getState() != OPENED) throw new IllegalStateException("Not opened");

		AudioFormat audioFormat = new AudioFormat(samplerate, 16, channels, true, ReflectionUtils.BIG_ENDIAN);
		sink.open(audioFormat);

		int qoaMaxFrameSize = MaxFrameSize(channels);
		inputBuffer.ensureCapacity(qoaMaxFrameSize);

		outputBuffer.ensureCapacity(SamplesPerFrame * channels * 2);

		Source in = this.in;
		while (this.in != null) {
			in.read(inputBuffer, qoaMaxFrameSize);
			if (!inputBuffer.isReadable()) break;

			int samplesDecoded = decodeFrame();

			sink.write(outputBuffer.list, 0, samplesDecoded * channels * 2);

			sample += samplesDecoded;
			inputBuffer.compact();
		}
		in = null;
	}

	public int decodeFrame() throws CorruptedInputException {
		var ib = inputBuffer;

		int checksum = CryptoFactory.CRC8(ib, 3);
		int syncword = ib.readMedium();
		if (checksum != ib.readUnsignedByte())
			throw new CorruptedInputException("syncword error: "+Integer.toHexString(syncword));

		int sampleCount = (syncword) & 0xFF;
		//int reserved = (syncword >>> 8) & 1;
		//int block = (syncword >>> 9) & 1;
		int data = (syncword >>> 10) & 1;
		int version = (syncword >>> 11) & 3;
		//int end = (syncword >>> 13) & 1;
		int channels = ((syncword >>> 14) & 15);
		//int bps = ((syncword >>> 18) & 3);
		int samplerate = SampleRates[((syncword >>> 20) & 7)];
		int lastFrame = ((syncword >>> 13) & 1);
		//int vbr = ((syncword >>> 23) & 1);
		int samples = (lastFrame == 0 ? MaxSlicesPerFrame : sampleCount) * SamplesPerSlice;

		if (version != 1 || channels != this.channels || samplerate != this.samplerate || data == 0)
			throw new CorruptedInputException("syncword error: "+Integer.toHexString(syncword));

		for (int c = 0; c < channels; c++) {
			for (int i = 0; i < LmsHistorySize; i++) {
				lms[c].history[i] = ib.readShort();
				lms[c].weights[i] = ib.readShort();
			}
		}

		Object obstart1 = outputBuffer.array();
		long obstart2 = outputBuffer._unsafeAddr();

		for (int sampleId = 0; sampleId < samples; sampleId += SamplesPerSlice) {
			for (int c = 0; c < channels; c++) {
				long slice = ib.readLong();
				int scalefactor = (int) ((slice >>> 60) & 0xf);
				int sliceStart = sampleId * channels + c;
				int sliceEnd = MathUtils.clamp(sampleId + SamplesPerSlice, 0, samples) * channels + c;

				for (int si = sliceStart; si < sliceEnd; si += channels) {
					int predicted = lms[c].predict();
					int quantized = (int) ((slice >>> 57) & 7);
					int dequantized = DequantizeTab[scalefactor][quantized];
					int reconstructed = clampS16(predicted + dequantized);

					U.putShort(obstart1, obstart2 + si*2L, (short) reconstructed);
					slice <<= 3;

					lms[c].update(reconstructed, dequantized);
				}
			}
		}

		return samples;
	}

	@Override public void disconnect() {
		in = null;
	}

	@Override public int getState() {
		if (in == null) return samplerate == 0 ? READY : FINISHED;
		return sample == 0 ? OPENED : DECODING;
	}

	@Override public boolean isSeekable() {return true;}
	@Override public void seek(double timeSec) throws IOException {
		var duration = getDuration();

		if (timeSec < 0) timeSec = 0;
		else if (timeSec > duration) timeSec = duration;

		long frame = (long)Math.floor(timeSec * samplerate / SamplesPerFrame);

		samples = frame * SamplesPerFrame;
		long seekPos = (samples == 0 ? 4 : 8) + frame / MaxFrameSize(channels);
		in.seek(seekPos);
	}
	@Override public double getCurrentTime() {return (double) sample / samplerate;}
	@Override public double getDuration() {
		if (samples != 0 || in == null) return (double) samples / samplerate;
		int maxFrameSize = MaxFrameSize(channels);
		try {
			long frameCount = ((in.length() - (samples == 0 ? 4 : 8)) + maxFrameSize-1) / maxFrameSize;
			return (double) (frameCount * SamplesPerFrame) / samplerate;
		} catch (Exception e) {
			return 0;
		}
	}
}