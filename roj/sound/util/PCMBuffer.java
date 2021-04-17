/*
 * Audio.java -- 音频输出
 */
package roj.sound.util;

import roj.util.ByteList;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class PCMBuffer implements IAudio {
	public ByteList buf;

	@Override
	public boolean open(AudioConfig h) {
		buf = new ByteList(1048576);
		return true;
	}

	@Override
	public int write(byte[] b, int size) {
		buf.put(b, 0, size);
		return size;
	}

	public void start(boolean b) {}

	@Override
	public void drain() {}

	@Override
	public void close() {}
}