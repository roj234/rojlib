package roj.sound.pipe;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/2/3 0003 14:36
 */
public class GainNode extends AudioPipeline {
	public float gain = 1;

	@Override
	public void process(AudioPipeline from, DynByteBuf pcm) {
		if (gain != 1) {
			int volume = (int) (gain * 4096);
			int i = pcm.rIndex;
			int stop = pcm.wIndex();
			if (gain == 0) {
				while (i < stop) pcm.putShort(i++, 0);
			} else {
				while (i < stop) {
					int v = (pcm.readShort(i) * volume) >> 12;
					if (v > 32767) v = 32767;
					else if (v < -32767) v = -32767;
					pcm.putShort(i++, v);
				}
			}
		}
		write(pcm);
	}
}
