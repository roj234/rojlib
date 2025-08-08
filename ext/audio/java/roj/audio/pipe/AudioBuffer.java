package roj.audio.pipe;

import roj.util.Helpers;

/**
 * @author Roj234-N
 * @since 2025/5/11 18:40
 */
public class AudioBuffer implements Cloneable {
	public float[][] data;
	public int sampleRate;
	public int channels;
	public boolean immutable;
	public int samples;

	public AudioBuffer toImmutable() {
		immutable = true;
		return this;
	}

	public AudioBuffer toMutable() {
		if (!immutable) return this;
		return clone();
	}

	@Override
	protected AudioBuffer clone() {
		AudioBuffer clone = null;
		try {
			clone = (AudioBuffer) super.clone();
		} catch (CloneNotSupportedException e) {
			Helpers.athrow(e);
		}
		clone.channels = channels;
		clone.sampleRate = sampleRate;
		clone.data = data.clone();
		for (int i = 0; i < data.length; i++) {
			clone.data[i] = data[i].clone();
		}
		return clone;
	}
}
