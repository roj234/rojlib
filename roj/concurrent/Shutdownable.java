package roj.concurrent;

/**
 * @author Roj233
 * @since 2021/12/25 0:27
 */
public interface Shutdownable {
	boolean wasShutdown();
	void shutdown();
}
