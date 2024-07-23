package roj.sound.record;

import roj.sound.AudioOutput;

import javax.sound.sampled.*;

/**
 * @author Roj234
 * @since 2020/12/19 22:36
 */
public class Recorder implements Runnable {
	public final AudioFormat format;
	protected final TargetDataLine line;
	protected final int once;
	protected final byte[] buf;
	protected final AudioOutput handler;

	public Recorder(int once, AudioOutput handler) throws LineUnavailableException {
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, this.format = getAudioFormat());
		line = (TargetDataLine) AudioSystem.getLine(info);
		this.once = once;
		this.handler = handler;
		this.buf = new byte[once];
	}

	protected AudioFormat getAudioFormat() {
		// 每秒播放和录制的 '样' 本数
		// 8000,11025,16000,22050,44100...
		float rate = 8000f;
		// 每个具有此格式的声音样本中的位数
		int sampleSize = 16;
		// 声道数
		int channels = 1;

		return new AudioFormat(rate, sampleSize, 2, true, true);
	}

	public void start() throws LineUnavailableException {
		line.open(format/*, buffer*/);
		line.start();
		handler.init(format, 4096);
	}

	@Override
	public void run() {
		final Thread self = Thread.currentThread();
		final byte[] buf = this.buf;

		int got;
		do {
			//line.available();
			got = line.read(buf, 0, once);
			if (got <= 0) {
				break;
			}
			handler.write(buf, 0, got, true);
		} while (!self.isInterrupted() && line.isOpen());
	}

	public void close() {
		line.close();
	}
}