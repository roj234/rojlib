package roj.ui;

import roj.text.CharList;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2022/11/19 0019 3:33
 */
public class ProgressBar implements AutoCloseable {
	static { CLIUtil.ensureInited(); }

	protected final CharList batch = new CharList();

	protected void render(CharList b) { CLIConsole.renderBottomLine(batch); }
	protected void dispose(boolean clearText) {
		CLIConsole.removeBottomLine(batch, clearText);
		barUpdate = dataUpdate = 0;
	}

	private String name, unit, prefix, postfix;
	private int barInterval, dataWindow;
	private long barUpdate, dataUpdate, eta;
	private boolean hideBar, hideSpeed;

	private long delta;

	public ProgressBar(String name) {
		this.name = name;
		this.unit = "it";
		this.barInterval = 200;
		this.dataWindow = 60000;
	}

	public void setName(String name) { this.name = name; }
	public void setUnit(String unit) { this.unit = unit; }
	public void setPrefix(String s) { this.prefix = s; }
	public void setPostfix(String s) { this.postfix = s; }
	public void setBarInterval(int bi) { this.barInterval = bi; }
	public void setDataWindow(int dw) { this.dataWindow = dw; }
	public void setHideBar(boolean b) { this.hideBar = b; }
	public void setHideSpeed(boolean hideSpeed) { this.hideSpeed = hideSpeed; }
	public double getEta(long remainUnit) { return remainUnit <= 0 ? 0 : remainUnit / speedPerMs(); }
	public double speedPerMs() { return dataUpdate == 0 ? 1 : (double) delta / (System.currentTimeMillis() - dataUpdate); }

	public void update(double percent, int deltaUnit) {
		long time = System.currentTimeMillis();
		synchronized (this) {
			long dtime = time - dataUpdate;
			if (dtime >= dataWindow) {
				delta = deltaUnit;
				dataUpdate = time-1;
			} else {
				delta += deltaUnit;
			}

			dtime = time - barUpdate;
			if (dtime < barInterval) return;
			barUpdate = time;
		}
		updateForce(percent);
	}

	// 进度条粒度
	private static final int PROGRESS_SIZE = 50;
	private static final int BITE = 100 / PROGRESS_SIZE;

	public synchronized void updateForce(double percent) {
		if (percent < 0) percent = 0;
		else if (percent > 1) percent = 1;
		// NaN
		else if (percent != percent) percent = 0;

		percent *= 100;

		batch.clear();
		CharList b = (name == null ? batch : batch.append("\u001b[0m").append(name).append(": ")).append("\u001B[96m");

		if (prefix == null) b.append(TextUtil.toFixedLength(percent, 4)).append("%");
		else b.append(prefix);

		if (!hideBar) {
			int tx = (int) percent / BITE;
			b.append("\u001B[97m├")
			 .append(TextUtil.repeat(tx, '█'))
			 .append(TextUtil.repeat(PROGRESS_SIZE - tx, '─'))
			 .append("┤\u001B[93m");
		}

		if (!hideSpeed) {
			double unitDelta = speedPerMs() * 1000;
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

			b.append(" ").append(unitDelta < 1000 ? TextUtil.toFixed(unitDelta, 2) : TextUtil.scaledNumber(Math.round(unitDelta))).append(unit).append('/').append(timeUnit);
		}

		if (postfix != null) b.append(' ').append(postfix);
		b.append("\u001B[0m");

		render(b);
	}

	@Override
	public void close() { end(); }

	public void end() { dispose(true); }
	public void end(String k) { end(k, CLIUtil.GREEN); }
	public void end(String k, int color) {
		batch.clear();
		CharList b = batch
			.append("\u001b[2K").append(name).append(": ")
			.append("\u001B[").append(color + 60).append('m')
			.append(k)
			.append("\u001B[0m");

		render(b);
		dispose(false);
	}
}
