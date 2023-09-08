package roj.text.logging.d;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/6/1 6:27
 */
public interface LogDestination {
	Appendable getAndLock();
	default void unlockAndFlush() throws IOException {}
}
