package roj.concurrent;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2021/2/17 21:38
 */
public interface Waitable {
	void waitFor() throws IOException;

	boolean isDone();

	void cancel();
}
