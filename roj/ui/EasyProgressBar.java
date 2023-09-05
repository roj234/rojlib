package roj.ui;

import roj.text.CharList;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2022/11/19 0019 3:33
 */
public class EasyProgressBar extends BottomLine {
	private String name, unit, percentStr;
	private int barInterval, dataInterval, dataWindow = 10000;
	private long barUpdate, dataUpdate;

	private double samples;
	private long delta;

	public EasyProgressBar(String name) {
		this.name = name;
		this.unit = "it";
		this.barInterval = 200;
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

			long dtime = time - dataUpdate;
			if (dtime >= dataInterval) {
				if (dtime >= dataWindow) {
					samples = (double) delta / dtime;
				} else {
					samples =
						samples * ((double) (dataWindow - dtime) / dataWindow) +
							(double) delta / dataWindow;
					// delta / dtime * dtime / dataWindow
				}

				delta = 0;
				dataUpdate = time;
			}

			dtime = time - barUpdate;
			if (dtime < barInterval) return;
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
		double unitDelta = samples * 1000;
		String timeUnit = "s";
		if (unitDelta < 1) {
			unitDelta *= 60;
			timeUnit = "m";
		}
		if (unitDelta < 1) {
			unitDelta *= 60;
			timeUnit = "h";
		}
		if (unitDelta < 1) {
			unitDelta *= 24;
			timeUnit = "d";
		}

		batch.clear();
		if (sysOutDeg == null) {
			CharList b = name == null ? batch : batch.append(name).append(": ");
			if (percentStr == null) {
				b.append(TextUtil.toFixedLength(percent, 4)).append("%");
			} else {
				b.append(percentStr);
			}
			b.append("├")
			 .append(TextUtil.repeat(tx, '█'))
			 .append(TextUtil.repeat(PROGRESS_SIZE - tx, '─'))
			 .append("┤  ").append(unitDelta < 1000 ? TextUtil.toFixed(unitDelta, 2) : TextUtil.scaledNumber(Math.round(unitDelta))).append(unit).append('/').append(timeUnit);
			sysOut.println(batch);
			return;
		}

		CharList b = (name == null ? batch : batch
			.append("\u001b[2K").append(name).append(": "))
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
		 .append("  ").append(unitDelta < 1000 ? TextUtil.toFixed(unitDelta, 2) : TextUtil.scaledNumber(Math.round(unitDelta))).append(unit).append('/').append(timeUnit)
		 .append("\u001B[0m");

		render(b);
	}

	@Override
	protected void doDispose() {
		dataUpdate = 0;
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
