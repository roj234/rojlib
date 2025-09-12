package roj.http.curl;

import roj.concurrent.Cancellable;
import roj.ui.EasyProgressBar;
import roj.ui.Tty;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Roj234
 * @since 2020/9/13 12:06
 */
public interface DownloadListener extends Cancellable {
	default void onStart(Downloader task) {}
	default void onProgress(Downloader task, int downloadedIncrement) {}
	default void onSuccess(Downloader task) {}

	class Single implements DownloadListener {
		final Collection<Downloader> workers = new ConcurrentLinkedQueue<>();
		EasyProgressBar progressBar = new EasyProgressBar("下载", "B");;
		boolean closed;

		public Single() {}

		@Override public void onStart(Downloader task) {
			progressBar.setName(task.owner.file.getName());
			progressBar.addTotal(task.getTotal());
			progressBar.increment(task.getDownloaded());
			workers.add(task);
		}
		@Override public void onProgress(Downloader task, int downloadedIncrement) {progressBar.increment(downloadedIncrement);}
		@Override public void onSuccess(Downloader task) {
			if (workers.remove(task) && workers.isEmpty()) {
				progressBar.end("下载完成");
			}
		}

		@Override public boolean isCancelled() {return closed;}
		@Override public boolean cancel(boolean mayInterruptIfRunning) {
			closed = true;
			progressBar.end("下载失败", Tty.RED);
			progressBar.close();
			return true;
		}

		public EasyProgressBar getProgressBar() {return progressBar;}
	}
}
