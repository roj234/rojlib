package roj.io.down;

import roj.concurrent.Shutdownable;

/**
 * @author Roj234
 * @since 2020/9/13 12:06
 */
public interface IProgress extends Shutdownable {
	default void onJoin(Downloader dn) {}
	default void onChange(Downloader dn) {}
	default void onFinish(Downloader downloader) {}
}
