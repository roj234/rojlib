package roj.media.audio;

import org.jetbrains.annotations.Nullable;
import roj.io.source.Source;
import roj.util.Helpers;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/2/19 3:23
 */
public interface AudioEncoder extends Closeable {
	default void start(Source out, AudioFormat format) throws IOException {start(out, format, null);}
	void start(Source out, AudioFormat format, @Nullable AudioMetadata meta) throws IOException;
	void finish() throws IOException;
	void write(byte[] b, int off, int len) throws IOException;

	default AudioSink asSink(Source out, @Nullable AudioMetadata metadata) {
		return new AudioSink() {
			@Override
			public void open(AudioFormat format) throws IOException {
				AudioEncoder.this.start(out, format, metadata);
			}

			@Override
			public void write(byte[] buf, int off, int len) {
				try {
					AudioEncoder.this.write(buf, off, len);
				} catch (IOException e) {
					Helpers.athrow(e);
				}
			}

			@Override
			public void close() {
				try {
					AudioEncoder.this.finish();
				} catch (IOException e) {
					Helpers.athrow(e);
				}
			}
		};
	}
}