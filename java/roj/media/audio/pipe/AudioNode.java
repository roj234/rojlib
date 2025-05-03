package roj.media.audio.pipe;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/2/3 14:45
 */
public abstract class AudioNode {
	AudioContext ctx;
	List<AudioNode> prev, child;

	public AudioContext ctx() {
		return ctx;
	}

	public static final int PROCESSOR = 0, SOURCE = 1, DRAIN = 2;
	public int type() {return PROCESSOR;}

	public void read(AudioBuffer ab) {}
	protected final void write(AudioBuffer ab) {
		for (AudioNode audioNode : child) {
			audioNode.read(ab);
		}
	}

	protected void close() {}
}
