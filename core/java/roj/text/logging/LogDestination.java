package roj.text.logging;

import roj.text.TextWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/6/1 6:27
 */
@FunctionalInterface
public interface LogDestination {
	Appendable getAndLock();
	default void unlockAndFlush() throws IOException {}

	static LogDestination stdout() {return appendTo(System.out);}
	static LogDestination appendTo(Appendable out) {return () -> out;}
	static LogDestination stream(OutputStream out) { return new Stream(out, null); }
	static LogDestination stream(OutputStream out, Charset charset) { return new Stream(out, charset); }

	final class Stream implements LogDestination {
		private final ReentrantLock lock;
		private final Appendable out;

		public Stream(OutputStream os, Charset charset) {
			lock = new ReentrantLock();
			out = os instanceof Appendable p ? p : new TextWriter(os, charset);
		}

		@Override
		public Appendable getAndLock() {
			lock.lock();
			return out;
		}

		@Override
		public void unlockAndFlush() throws IOException {
			try {
				if (out instanceof TextWriter tw)
					tw.flush();
			} finally {
				lock.unlock();
			}
		}
	}
}