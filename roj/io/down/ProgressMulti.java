package roj.io.down;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/13 12:33
 */
public class ProgressMulti extends ProgressSimple {
	protected final List<Downloader> workers;

	public ProgressMulti() {
		workers = new ArrayList<>();
	}

	@Override
	public void onFinish(Downloader dn) {
		synchronized (this) {
			workers.remove(dn);
			if (workers.isEmpty()) super.onFinish(dn);
		}
	}

	@Override
	public void onJoin(Downloader dn) {
		workers.add(dn);
	}

	@Override
	public void onChange(Downloader dn) {
		long t = System.currentTimeMillis();
		if (t - last < sampleInterval && dn.getRemain() > 0) return;
		last = t;

		long sumDown = 0, sumTot = 0, sumByte = 0;
		for (int i = 0; i < workers.size(); i++) {
			Downloader d = workers.get(i);
			sumDown += d.getDownloaded();
			sumTot += d.getTotal();
			sumByte += d.getAverageSpeed();
		}

		bar.update((double) sumDown / sumTot, (int) sumByte);
	}
}
