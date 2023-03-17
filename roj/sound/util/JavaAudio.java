/*
 * Audio.java -- 音频输出
 */
package roj.sound.util;

import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class JavaAudio implements IAudio {
	private SourceDataLine out;
	@Override
	public boolean open(AudioConfig h) {
		AudioFormat af = new AudioFormat(h.getSamplingRate(), 16, h.channels(), true, false);
		try {
			if (out == null) out = AudioSystem.getSourceDataLine(af);
			if (out.isOpen()) out.close();
			out.open(af, 8 * h.getPcmSize());
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			return false;
		}

		out.start();
		return true;
	}

	public void mute(boolean mute) {
		if (out == null) return;
		((BooleanControl) out.getControl(BooleanControl.Type.MUTE)).setValue(mute);
	}

	public void setVolume(float vol) {
		if (out == null) return;
		((FloatControl) out.getControl(Type.MASTER_GAIN)).setValue(vol);
	}

	public float getVolume() {
		return ((FloatControl) out.getControl(Type.MASTER_GAIN)).getValue();
	}

	@Override
	public int write(byte[] b, int size) {
		if (out == null) return -1;
		return out.write(b, 0, size);
	}

	public void start(boolean b) {
		if (out == null) return;
		if (b) {out.start();} else out.stop();
	}

	@Override
	public void drain() {
		if (out != null) out.drain();
	}

	@Override
	public void close() {
		if (out != null) {
			out.stop();
			out.close();
			out = null;
		}
	}
}