package roj.ui;

import roj.math.MathUtils;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.TextUtil;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2022/11/19 0019 3:33
 */
public class ProgressBar implements AutoCloseable {
	private static final int BAR_FPS = 40;

	protected final CharList batch = new CharList();

	protected void render(CharList b) {Terminal.renderBottomLine(batch);}
	protected void dispose(boolean clearText) {
		Terminal.removeBottomLine(batch, clearText);
		barUpdate = 0;
	}

	protected String unit;
	private String name, prefix, postfix;
	private long barUpdate, dataUpdate;
	private boolean hideBar, hideSpeed;

	private long delta;
	private static final long DELTA_OFFSET = ReflectionUtils.fieldOffset(ProgressBar.class, "delta");
	private static final long UPDATE_OFFSET = ReflectionUtils.fieldOffset(ProgressBar.class, "barUpdate");

	public ProgressBar(String name) {
		this.name = name;
		this.unit = "it";
	}

	public void setName(String name) { this.name = name; }
	public void setUnit(String unit) { this.unit = unit; }
	public void setPrefix(String s) { this.prefix = s; }
	public void setPostfix(String s) { this.postfix = s; }
	public void setHideBar(boolean b) { this.hideBar = b; }
	public void setHideSpeed(boolean hideSpeed) { this.hideSpeed = hideSpeed; }
	public double getEta(long remainUnit) { return remainUnit <= 0 ? 0 : remainUnit / speedPerMs(); }
	public double speedPerMs() { return dataUpdate == 0 ? 1 : (double) delta / (System.currentTimeMillis() - dataUpdate); }

	public void reset() {
		delta = 0;
		dataUpdate = 0;
	}

	public void update(double percent, int deltaUnit) {
		long time = System.currentTimeMillis();

		u.getAndAddLong(this, DELTA_OFFSET, deltaUnit);

		long t = barUpdate;
		if (time - t < BAR_FPS || !u.compareAndSwapLong(this, UPDATE_OFFSET, t, time)) return;

		if (time - dataUpdate > 2000) {
			delta = (long) (speedPerMs() * 1000);
			dataUpdate = time - 1000;
		}

		updateForce(percent);
	}

	private static final String TIMER = "|/-\\";
	private int timerId;
	private int prevWidth;
	public synchronized void updateForce(double percent) {
		percent = MathUtils.clamp(percent, 0, 1);

		batch.clear();
		CharList b = (name == null ? batch : batch.append("\u001b[0m").append(name).append(": ")).append("\u001B[96m");

		if (prefix == null) b.append(TextUtil.toFixedLength(percent*100, 4)).append('%');
		else b.append(prefix);

		if (!hideBar) {
			int width = Terminal.getStringWidth(b);
			int pad = 0;
			if (width > prevWidth || width < prevWidth - 5) {
				prevWidth = width;
			} else {
				pad = prevWidth - width;
				width = prevWidth;
			}

			if (postfix != null) width += Terminal.getStringWidth(postfix)+1;
			if (!hideSpeed) width += Terminal.getStringWidth(unit)+9;

			int progressWidth = Terminal.windowWidth - width - 7;
			if (progressWidth < 10) progressWidth = 10;

			int tx = (int) Math.round(percent * progressWidth);

			b.padEnd(' ', pad)
			 .append("\u001B[97m [");
			Terminal.MinecraftColor.sonic("=".repeat(tx), b);
			b.padEnd(' ', progressWidth - tx)
			 .append("]\u001B[93m ").append(TIMER.charAt(timerId = (timerId+1) & 3));
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

			b.append(' ').append(unitDelta < 1000 ? TextUtil.toFixed(unitDelta, 2) : TextUtil.scaledNumber(Math.round(unitDelta))).append(unit).append('/').append(timeUnit);
		}

		if (postfix != null) b.append(' ').append(postfix);
		b.append("\u001B[0m");

		render(b);
	}

	@Override
	public void close() { end(); }

	public void end() { dispose(true); }
	public void end(String k) { end(k, Terminal.GREEN); }
	public void end(String k, int color) {
		batch.clear();
		CharList b = batch
			.append("\u001b[2K").append(name).append(": ")
			.append("\u001B[").append(color+ Terminal.HIGHLIGHT).append('m')
			.append(k)
			.append("\u001B[0m");

		render(b);
		dispose(false);
	}
}