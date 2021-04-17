package roj.text.logging.d;

import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/6/1 6:27
 */
public class LDStream implements LogDestination {
	protected ReentrantLock lock;
	protected OutputStream out;

	public static LDStream of(OutputStream out) {
		return new LDStream(out);
	}

	public LDStream(OutputStream out) {
		lock = new ReentrantLock(true);
		this.out = out;
	}

	@Override
	public OutputStream getAndLock() {
		lock.lock();
		return out;
	}

	@Override
	public void unlock() {
		lock.unlock();
	}
}
