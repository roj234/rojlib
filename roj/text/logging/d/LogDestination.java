package roj.text.logging.d;

import java.io.OutputStream;

/**
 * @author Roj233
 * @since 2022/6/1 6:27
 */
public interface LogDestination {
	default OutputStream getAndLock() {
		return null;
	}

	default void unlock() {}

	default void newLine(CharSequence cs) {}
}
