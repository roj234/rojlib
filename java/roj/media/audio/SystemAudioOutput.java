package roj.media.audio;

import roj.util.Helpers;

import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;
import java.nio.channels.ClosedByInterruptException;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class SystemAudioOutput implements AudioOutput {
	private static boolean cannotReopen傻逼java;

	private AudioFormat format;
	private SourceDataLine out;
	private boolean paused;

	private boolean muted;
	private float gain = 1;

	@Override
	public void init(AudioFormat h, int buffer) throws LineUnavailableException {
		if (format != null && h.matches(format)) return;

		int frameSize = h.getFrameSize();
		int cap = (int) (h.getFrameRate()*frameSize)/2;
		int halfSecond = (cap * frameSize + frameSize - 1) / frameSize;
		if (buffer < halfSecond) buffer = halfSecond;

		format = h;
		if (out != null) {
			if (out.isOpen()) out.close();
			if (!cannotReopen傻逼java) {
				try {
					out.open(h, buffer);
					out.start();
					return;
				} catch (Exception e) {
					cannotReopen傻逼java = true;
					System.err.println("cannotReopen傻逼java = true");
					close();
				}
			}
		}
		out = AudioSystem.getSourceDataLine(h);
		out.open(h, buffer);
		out.start();
		mute(muted);
		setVolume(gain);
	}

	@Override
	public void close() {
		out.close();
		format = null;
	}

	@Override
	public void flush() {out.drain();}

	public void mute(boolean mute) {
		muted = mute;
		if (out == null) return;
		((BooleanControl) out.getControl(BooleanControl.Type.MUTE)).setValue(mute);
	}
	public void setVolume(float vol) {
		gain = vol;
		if (out == null) return;
		((FloatControl) out.getControl(Type.MASTER_GAIN)).setValue(vol);
	}
	public float getVolume() {return ((FloatControl) out.getControl(Type.MASTER_GAIN)).getValue();}

	@Override
	public int write(byte[] b, int off, int len, boolean blocking) {
		synchronized (this) {
			while (paused) {
				try {
					wait();
				} catch (InterruptedException e) {
					close();
					Thread.currentThread().interrupt();
					Helpers.athrow(new ClosedByInterruptException());
				}
			}
		}

		if (!blocking) {
			int max = out.available();
			if (len > max) len = max;
		}

		out.write(b, 0, len);
		return len;
	}

	public boolean paused() { return out != null && !out.isActive(); }
	public synchronized void pause() {
		paused = !paused;
		if (!paused) notifyAll();
	}
}