package roj.sound;

import roj.util.ByteList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class MemoryAudioOutput implements AudioOutput {
	private AudioFormat format;
	private ByteList data = new ByteList();
	@Override
	public void start(AudioFormat h, int buffer) throws LineUnavailableException {
		this.format = h;
		this.data.clear();
	}

	@Override
	public void stop() {}

	@Override
	public void flush() {}

	@Override
	public void write(byte[] b, int size) {
		this.data.put(b, 0, size);
	}
}