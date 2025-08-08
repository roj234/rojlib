package roj.audio;

import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;
import java.io.IOException;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 */
public class SpeakerSink implements AudioSink {
	private static boolean cannotReopen傻逼java;

	private AudioFormat format;
	private SourceDataLine out;
	private boolean paused;

	private boolean muted;
	private float gain = 1;

	@Override
	public void open(AudioFormat h) throws IOException {
		if (format != null && h.matches(format)) return;

		int frameSize = h.getFrameSize();
		int cap = (int) (h.getFrameRate()*frameSize)/2;
		int halfSecond = (cap * frameSize + frameSize - 1) / frameSize;

		format = h;
		if (out != null) {
			if (out.isOpen()) out.close();
			if (!cannotReopen傻逼java) {
				try {
					out.open(h, halfSecond);
					out.start();
					return;
				} catch (Exception e) {
					cannotReopen傻逼java = true;
					System.err.println("cannotReopen傻逼java = true");
					close();
				}
			}
		}
		try {
			out = AudioSystem.getSourceDataLine(h);
			out.open(h, halfSecond);
		} catch (LineUnavailableException e) {
			throw new IOException(e);
		}
		paused = false;
		out.start();
		mute(muted);
		setVolume(gain);
	}

	@Override
	public void write(byte[] b, int off, int len) {
		synchronized (this) {
			while (paused) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IllegalStateException("interrupted");
				}
			}
		}

		out.write(b, 0, len);
	}

	@Override
	public int writeNow(byte[] buf, int off, int len) {
		len = Math.min(out.available(), len);
		out.write(buf, off, len);
		return len;
	}

	@Override
	public void flush() {out.drain();}

	@Override
	public void close() {
		out.close();
		paused = false;
		format = null;
	}

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

	public boolean paused() { return paused; }
	public synchronized void pause() {
		paused = !paused;
		notifyAll();
	}
}