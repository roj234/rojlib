package roj.ui;

import roj.collect.AbstractIterator;
import roj.collect.IntMap;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2022/11/19 0019 3:33
 */
public class EasyProgressBar extends BottomLine {
	private String name, unit, percentStr;
	private int barInterval, dataInterval;
	private long barUpdate, dataUpdate;

	private double sampleSize, samples;
	private long delta;

	public static <T> Iterable<T> iterable(Iterable<T> iterable, String desc, int total) {
		return iterable(iterable, desc, total, true, "it");
	}
	public static <T> Iterable<T> iterable(Iterable<T> iterable, String desc, int total, boolean leave, String unit) {
		EasyProgressBar bar = new EasyProgressBar(desc);
		bar.unit = unit;

		return () -> new AbstractIterator<T>() {
			final Iterator<T> itr = iterable.iterator();
			int pos;

			{ result = Helpers.cast(IntMap.UNDEFINED); }

			@Override
			protected boolean computeNext() {
				if (itr.hasNext()) {
					if (result != IntMap.UNDEFINED) {
						bar.update((double) ++pos / total, 1);
					}
					result = itr.next();
					return true;
				} else {
					if (leave) bar.end("完成");
					bar.dispose(!leave);
					return false;
				}
			}
		};
	}

	public EasyProgressBar(String name) {
		this.name = name;
		this.unit = "it";
		this.barInterval = 100;
		this.dataInterval = 200;
	}

	public void setName(String name) {
		this.name = name;
	}
	public void setUnit(String unit) {
		this.unit = unit;
	}
	public void setPercentStr(String percentStr) {
		this.percentStr = percentStr;
	}
	public void setBarInterval(int barInterval) {
		this.barInterval = barInterval;
		if (dataInterval > barInterval) dataInterval = barInterval;
	}
	public void setDataInterval(int dataInterval) {
		this.dataInterval = dataInterval;
	}

	public void update(double percent, int deltaUnit) {
		long time = System.currentTimeMillis();
		synchronized (this) {
			delta += deltaUnit;
			long dtime = time - barUpdate;
			if (dtime < barInterval) return;

			dtime = time - dataUpdate;
			if (dtime >= dataInterval) {
				addSample(delta * ((double)dataInterval / dtime));
				delta = 0;
				dataUpdate = time;
			}
			barUpdate = time;
		}
		updateForce(percent);
	}

	public void updateForce(double percent) {
		if (percent < 0) percent = 0;
		else if (percent > 1) percent = 1;
		// NaN
		else if (percent != percent) percent = 0;

		percent *= 100;
		int tx = (int) percent / BITE;
		long unitDelta = Math.round(samples / dataInterval * 1000);

		batch.clear();
		if (sysOutDeg == null) {
			CharList b = batch.append(name).append(": ");
			if (percentStr == null) {
				b.append(TextUtil.toFixedLength(percent, 4)).append("%");
			} else {
				b.append(percentStr);
			}
			b.append("├")
			 .append(TextUtil.repeat(tx, '█'))
			 .append(TextUtil.repeat(PROGRESS_SIZE - tx, '─'))
			 .append("┤  ").append(TextUtil.scaledNumber(unitDelta).toUpperCase()).append(unit).append("/S");
			sysOut.println(batch);
			return;
		}

		CharList b = batch
			.append("\u001b[2K").append(name).append(": ")
			.append("\u001B[").append(CmdUtil.Color.CYAN + 60).append('m');
		if (percentStr == null) {
			b.append(TextUtil.toFixedLength(percent, 4)).append("%");
		} else {
			b.append(percentStr);
		}
		b.append("\u001B[").append(CmdUtil.Color.WHITE + 60).append('m')
		 .append("├")
		 .append(TextUtil.repeat(tx, '█'))
		 .append(TextUtil.repeat(PROGRESS_SIZE - tx, '─'))
		 .append("┤")
		 .append("\u001B[").append(CmdUtil.Color.YELLOW + 60).append('m')
		 .append("  ").append(TextUtil.scaledNumber(unitDelta).toUpperCase()).append(unit).append("/S")
		 .append("\u001B[0m");

		render(b);
	}

	private void addSample(double unit) {
		// keep last 50 samples
		if (sampleSize > 49) sampleSize = 49;
		samples = (samples * sampleSize++ + unit) / sampleSize;
	}

	public void end(String k) {
		end(k, CmdUtil.Color.GREEN);
	}
	public void end(String k, int color) {
		if (sysOutDeg == null) {
			batch.clear();
			sysOut.println(batch.append(name).append(": ").append(k));
			return;
		}

		batch.clear();
		CharList b = batch.append("\u001b[2K").append(name).append(": ")
						  .append("\u001B[").append(color + 60).append('m')
						  .append(k)
						  .append("\u001B[0m");

		render(b);
	}

	// 进度条粒度
	private static final int PROGRESS_SIZE = 50;
	private static final int BITE = 100 / PROGRESS_SIZE;
}
