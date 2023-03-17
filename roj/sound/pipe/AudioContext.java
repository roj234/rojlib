package roj.sound.pipe;

import roj.collect.Graph;
import roj.io.buf.BitmapBPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/2/3 0003 14:49
 */
public class AudioContext {
	Graph<AudioPipeline> graph;
	BitmapBPool pool = new BitmapBPool(262144, 512);
	DynByteBuf buffer;

	public void register(AudioPipeline pipeline) {
		graph.addNode(pipeline);
	}

	public void connect(AudioPipeline from, AudioPipeline to) {
		graph.addEdge(from, to);
	}

	public void disconnect(AudioPipeline from, AudioPipeline to) {
		graph.removeEdge(from, to);
	}

	public void disconnectFrom(AudioPipeline from) {
		graph.removeEdgeFrom(from);
	}

	public void disconnectTo(AudioPipeline to) {
		graph.removeEdgeTo(to);
	}

	public void initSource() {
		//graph.checkLoop();
	}

	public void processInput(DynByteBuf buf) {

	}
}
