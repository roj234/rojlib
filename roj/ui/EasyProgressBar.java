package roj.ui;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Roj234
 * @since 2023/10/28 0028 1:55
 */
public class EasyProgressBar extends ProgressBar {
	final LongAdder fin = new LongAdder();
	final AtomicLong max = new AtomicLong();

	public EasyProgressBar(String name) { super(name); }
	public EasyProgressBar(String name, String unit) { super(name); setUnit(unit); }

	public void reset() {
		fin.reset();
		max.set(0);
	}

	public void addMax(long count) { max.addAndGet(count); }

	public void addCurrent(int count) {
		fin.add(count);

		long sum = fin.sum();
		long tot = max.get();

		setPrefix(sum+"/"+tot);
		int s = (int) (getEta(tot-sum) / 1000);
		setPostfix("ETA: "+s/3600+":"+s/60%60+":"+s%60);

		double pct = (double) sum / tot;
		update(pct, count);
		if (pct >= 1) updateForce(pct);
	}
}
