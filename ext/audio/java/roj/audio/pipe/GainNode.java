package roj.audio.pipe;

/**
 * @author Roj234
 * @since 2023/2/3 14:36
 */
public class GainNode extends AudioNode {
	public float gain = 1;

	@Override
	public void read(AudioBuffer ab) {
		ab = ab.toMutable();

		for (int i = 0; i < ab.channels; i++) {
			for (int j = 0; j < ab.samples; j++) {
				ab.data[i][j] *= gain;
			}
		}

		write(ab);
	}
}
