package roj.ui;

import roj.io.IOUtil;
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
		super.reset();
	}

	public void addMax(long count) { max.addAndGet(count); }
	public void addCurrent(long count) {
		fin.add(count);

		long sum = fin.sum();
		long tot = max.get();

		double pct = (double) sum / tot;
		update(pct, (int) Math.min(count, Integer.MAX_VALUE));
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

		if (speedPerMs() == 0) {
			setPostfix("ETA: --:--");
		} else {
			int s = (int) (getEta(tot-sum) / 1000);
			setPostfix(IOUtil.getSharedCharBuf().append("ETA: ").append(s/3600).append(':').padNumber(s/60%60, 2).append(':').padNumber(s%60, 2).toString());
		}

		super.updateForce(percent);
	}

	public long getCurrent() {return fin.sum();}
	public long getMax() {return max.get();}
}