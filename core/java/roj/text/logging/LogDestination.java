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

	// allow System.setOut() to work properly
	static LogDestination stdout() {return new Synchronized(() -> System.out);}
	static LogDestination appendTo(Appendable out) {return new Synchronized(() -> out);}
	static LogDestination to(OutputStream out) { return new Stream(out, null); }
	static LogDestination to(OutputStream out, Charset charset) { return new Stream(out, charset); }

	class Synchronized implements LogDestination {
		private final ReentrantLock lock;
		private final LogDestination destination;

		public Synchronized(LogDestination destination) {
			lock = new ReentrantLock();
			this.destination = destination;
		}

		@Override
		public Appendable getAndLock() {
			lock.lock();
			return destination.getAndLock();
		}

		@Override
		public void unlockAndFlush() throws IOException {
			try {
				destination.unlockAndFlush();
			} finally {
				lock.unlock();
			}
		}
	}

	final class Stream implements LogDestination {
		private final Appendable out;

		public Stream(OutputStream os, Charset charset) {
			out = os instanceof Appendable p ? p : new TextWriter(os, charset);
		}

		@Override
		public Appendable getAndLock() {return out;}

		@Override
		public void unlockAndFlush() throws IOException {
			if (out instanceof TextWriter tw)
				tw.flush();
		}
	}
}