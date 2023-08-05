package roj.ui;

import roj.text.TextUtil;

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

		double pct = (double) sum / tot;
		update(pct, count);
		if (pct >= 1) updateForce(pct);
	}

	@Override
	public void updateForce(double percent) {
		long sum = fin.sum();
		long tot = max.get();

		if (unit.equals("B")) {
			setPrefix(TextUtil.scaledNumber1024(sum)+"/"+TextUtil.scaledNumber1024(tot));
		} else {
			setPrefix(sum+"/"+tot);
		}
		int s = (int) (getEta(tot-sum) / 1000);
		setPostfix("ETA: "+s/3600+":"+s/60%60+":"+s%60);

		super.updateForce(percent);
	}
}
