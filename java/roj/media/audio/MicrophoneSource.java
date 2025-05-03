package roj.media.audio;

import roj.util.ArrayCache;

import javax.sound.sampled.*;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2020/12/19 22:36
 */
public class MicrophoneSource implements AudioSource {
	public final AudioFormat format;
	protected final TargetDataLine line;
	protected final int bufferSize;

	public MicrophoneSource() throws LineUnavailableException {this(4096);}
	public MicrophoneSource(int bufferSize) throws LineUnavailableException {this(bufferSize, defaultFormat());}
	private static AudioFormat defaultFormat() {
		return new AudioFormat(44100, 16, 2, true, false);
	}
	public MicrophoneSource(int bufferSize, AudioFormat format) throws LineUnavailableException {
		this.format = format;
		this.bufferSize = bufferSize;

		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		line = (TargetDataLine) AudioSystem.getLine(info);
		line.open(format/*, buffer*/);
	}

	@Override
	public void connect(AudioSink sink) throws IOException {
		line.start();

		final byte[] buf = ArrayCache.getByteArray(bufferSize, false);
		try {
			sink.open(format);

			while (line.isActive()) {
				int read = line.read(buf, 0, bufferSize);
				if (read <= 0) break;

				sink.write(buf, 0, read);
			}
		} finally {
			ArrayCache.putArray(buf);
			line.stop();
		}
	}

	@Override
	public void disconnect() {
		line.stop();
	}
}