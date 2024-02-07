package roj.text.logging.d;

import roj.text.TextWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/6/1 6:27
 */
public class LDStream implements LogDestination {
	private final ReentrantLock lock;
	private final TextWriter out;

	public static LDStream of(OutputStream out) { return new LDStream(out, null); }
	public static LDStream of(OutputStream out, Charset cs) { return new LDStream(out, cs); }

	public LDStream(OutputStream os, Charset charset) {
		lock = new ReentrantLock(true);
		out = new TextWriter(os, charset);
	}

	@Override
	public Appendable getAndLock() {
		lock.lock();
		return out;
	}

	@Override
	public void unlockAndFlush() throws IOException {
		try {
			out.flush();
		} finally {
			lock.unlock();
		}
	}
}