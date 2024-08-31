package roj.media.audio.pipe;

import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/2/3 0003 14:45
 */
public abstract class AudioPipeline {
	AudioContext ctx;
	List<AudioPipeline> prev, child;

	public AudioContext ctx() {
		return ctx;
	}
	public int inputChannels() {
		return -1;
	}
	public int outputChannels() {
		return -1;
	}

	void write(DynByteBuf pcm) {
		//ctx.handleWrite();
	}

	public abstract void process(AudioPipeline from, DynByteBuf pcm);

	protected void close() {}
}
