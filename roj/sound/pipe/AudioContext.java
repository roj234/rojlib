package roj.sound.pipe;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/2/3 0003 14:49
 */
public class AudioContext {
	DynByteBuf buffer;

	public void register(AudioPipeline pipeline) {
	}

	public void connect(AudioPipeline from, AudioPipeline to) {
	}

	public void disconnect(AudioPipeline from, AudioPipeline to) {
	}

	public void disconnectFrom(AudioPipeline from) {
	}

	public void disconnectTo(AudioPipeline to) {
	}

	public void initSource() {
		//graph.checkLoop();
	}

	public void processInput(DynByteBuf buf) {

	}
}