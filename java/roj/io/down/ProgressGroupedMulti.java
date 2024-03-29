package roj.io.down;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Roj234
 * @since 2020/9/13 12:33
 */
public class ProgressGroupedMulti extends ProgressSimple {
	protected final ConcurrentLinkedQueue<Downloader> workers;
	public int finished, all;

	public ProgressGroupedMulti() {
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
		workers.add(dn);
	}

	@Override
	public void onChange(Downloader dn) {
		long t = System.currentTimeMillis();
		if (t - last < sampleInterval) return;
		last = t;

		long sumDown = 0;
		for (Iterator<Downloader> itr = workers.iterator(); itr.hasNext(); ) {
			Downloader d = itr.next();
			sumDown += d.getDelta();
			if (d.getRemain() <= 0) itr.remove();
		}

		bar.update((double) finished / all, (int) sumDown);
	}

	public IProgress subProgress() {
		return new IProgress() {
			boolean kill;
			volatile int count;

			@Override
			public boolean wasShutdown() {
				return kill;
			}

			@Override
			public void shutdown() {
				kill = true;
			}

			@Override
			public void onJoin(Downloader dn) {
				ProgressGroupedMulti.this.onJoin(dn);
				count++;
			}

			@Override
			public void onChange(Downloader dn) {
				ProgressGroupedMulti.this.onChange(dn);
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
				if (workers.isEmpty()) ProgressGroupedMulti.this.onFinish(dn);
			}
		};
	}
}
