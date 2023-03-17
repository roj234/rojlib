package ilib.anim;

import ilib.anim.timing.Timing;
import ilib.client.RenderUtils;
import roj.math.Mat4x3f;

/**
 * @author solo6975
 * @since 2022/4/7 12:55
 */
public class Animation {
	final Timing tf;
	private final Keyframes keyframes;
	private final int kfDuration;

	int duration;
	boolean playing;
	long begin;

	public Animation(Keyframes kf, Timing tf) {
		this.tf = tf;
		this.keyframes = kf;
		this.kfDuration = kf.duration();
	}

	public void apply() {
		if (!playing) return;
		double pc = tf.interpolate((double) (System.currentTimeMillis() - begin) / duration);
		Mat4x3f mat = keyframes.interpolate(pc * kfDuration);
		RenderUtils.loadMatrix(mat);
		if (pc >= 1) playing = false;
	}

	public Keyframes getKeyframes() {
		return keyframes;
	}

	public int getDuration() {
		return duration;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	public boolean isPlaying() {
		return playing;
	}

	public void play() {
		playing = true;
		begin = System.currentTimeMillis();
	}

	public void stop() {
		playing = false;
	}
}
