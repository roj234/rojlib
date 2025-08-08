package roj.http.curl;

import roj.ui.EasyProgressBar;
import roj.ui.Tty;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Roj234
 * @since 2020/9/13 12:06
 */
public interface DownloadListener extends Closeable {
	boolean isClosed();
	void close();

	default void onJoin(Downloader dn) {}
	default void onChange(Downloader dn) {}
	default void onFinish(Downloader downloader) {}

	class Single implements DownloadListener {
		public void onFinish(Downloader dn) {
			bar.setName(dn.owner.file.getName());
			bar.end("下载完成");
		}

		protected EasyProgressBar bar;
		boolean closed;

		public EasyProgressBar bar() {return bar;}

		public Single() {
			bar = new EasyProgressBar("下载", "B");
		}

		@Override public boolean isClosed() {return closed;}
		@Override public void close() {bar.end("下载失败", Tty.RED);closed = true;}

		@Override public void onJoin(Downloader dn) {bar.addTotal(dn.getTotal());}
		@Override public void onChange(Downloader dn) {bar.increment(dn.getDelta());}
	}

	class Multi extends Single {
		protected final List<Downloader> workers = new ArrayList<>();

		@Override
		public synchronized void onFinish(Downloader dn) {
			workers.remove(dn);
			if (workers.isEmpty()) super.onFinish(dn);
		}

		@Override
		public synchronized void onJoin(Downloader dn) {
			workers.add(dn);
			super.onJoin(dn);
		}
	}

	class Grouped extends Single {
		protected final ConcurrentLinkedQueue<Downloader> workers;
		public int finished, all;

		public Grouped() {
			workers = new ConcurrentLinkedQueue<>();
			bar.setName("合计进度");
		}

		public void setFinished(int finished) {
			this.finished = finished;
		}
		public void setTotal(int all) {
			this.all = all;
		}

		@Override
		public void onJoin(Downloader dn) {
			super.onJoin(dn);
			workers.add(dn);
		}

		public DownloadListener subProgress() {
			return new DownloadListener() {
				boolean closed;
				volatile int count;

				@Override public boolean isClosed() {return closed;}
				@Override public void close() {closed = true;}

				@Override
				public void onJoin(Downloader dn) {
					Grouped.this.onJoin(dn);
					count++;
				}

				@Override
				public void onChange(Downloader dn) {
					Grouped.this.onChange(dn);
				}

				@Override
				public void onFinish(Downloader dn) {
					for (Iterator<Downloader> itr = workers.iterator(); itr.hasNext(); ) {
						Downloader d = itr.next();
						if (d.getRemain() <= 0) itr.remove();
					}
					synchronized (this) {
						if (--count == 0) {
							finished++;
							System.out.println(dn.owner.file.getName() + ": done");
						}
					}
					if (workers.isEmpty()) Grouped.this.onFinish(dn);
				}
			};
		}
	}
}
