package roj.sound;

import roj.io.source.Source;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/2/19 3:23
 */
public interface AudioEncoder extends Closeable {
	void start(Source out, AudioFormat pcm, AudioMetadata meta) throws IOException;
	void stop();
	void write(byte[] b, int off, int len) throws IOException;
}