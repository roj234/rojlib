package roj.sound.pipe;

import roj.util.DynByteBuf;

import javax.sound.sampled.*;

/**
 * @author Roj234
 * @since 2023/2/3 0003 15:07
 */
public class SpeakerNode extends AudioPipeline {
	private final SourceDataLine out;

	public SpeakerNode(int samplingRate, int channels, int nativeBufferMs) throws LineUnavailableException {
		AudioFormat af = new AudioFormat(samplingRate, 16, channels, true, false);

		int len = (int)Math.ceil(nativeBufferMs * af.getFrameRate() / 1000F) * af.getFrameSize();

		out = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, af, len));
	}

	@Override
	public int inputChannels() {
		return out.getFormat().getChannels();
	}

	@Override
	public int outputChannels() {
		return 0;
	}

	public void mute(boolean mute) {
		if (out == null) return;
		((BooleanControl) out.getControl(BooleanControl.Type.MUTE)).setValue(mute);
	}

	public void setVolume(float vol) {
		if (out == null) return;
		((FloatControl) out.getControl(FloatControl.Type.MASTER_GAIN)).setValue(vol);
	}

	public float getVolume() {
		return ((FloatControl) out.getControl(FloatControl.Type.MASTER_GAIN)).getValue();
	}

	public void drain() {
		if (out != null) out.drain();
	}

	@Override
	public void process(AudioPipeline from, DynByteBuf pcm) {
		out.write(pcm.array(), pcm.arrayOffset(), pcm.readableBytes());
	}

	@Override
	protected void close() {
		out.close();
	}
}
