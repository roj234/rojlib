package roj.sound;

import roj.util.Helpers;

import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;
import java.nio.channels.ClosedByInterruptException;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class SystemAudioOutput implements AudioOutput {
	private AudioFormat format;
	private SourceDataLine out;
	private boolean paused;
	@Override
	public void start(AudioFormat h, int buffer) throws LineUnavailableException {
		if (format != null && h.matches(format)) return;
		if (out != null) stop();
		out = AudioSystem.getSourceDataLine(h);
		format = h;

		int frameSize = h.getFrameSize();
		int cap = (int) (h.getFrameRate()*frameSize / 10);
		out.open(h, (cap*frameSize + frameSize-1) / frameSize);
		out.start();
	}

	@Override
	public void stop() {
		out.stop();
		out.close();
		format = null;
	}

	@Override
	public void flush() { out.drain(); }

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
	public void write(byte[] b, int size) {
		synchronized (this) {
			while (paused) {
				try {
					wait();
				} catch (InterruptedException e) {
					stop();
					Thread.currentThread().interrupt();
					Helpers.athrow(new ClosedByInterruptException());
				}
			}
		}
		out.write(b, 0, size);
	}

	public boolean paused() { return out != null && !out.isActive(); }
	public synchronized void pause() {
		if (paused = !paused) notify();
	}
}