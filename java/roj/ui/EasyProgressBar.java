package roj.ui;

import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.TextUtil;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/10/28 0028 1:55
 */
public class EasyProgressBar extends ProgressBar {
	private static final int AVG_SPEED_WINDOW = 2000;

	private String unit, postfix = "--:--";
	private volatile long deltaTime, delta;
	private volatile long finish, total;

	private static final long
		DELTA_OFFSET = ReflectionUtils.fieldOffset(EasyProgressBar.class, "delta"),
		FINISH_OFFSET = ReflectionUtils.fieldOffset(EasyProgressBar.class, "finish"),
		TOTAL_OFFSET = ReflectionUtils.fieldOffset(EasyProgressBar.class, "total");

	public EasyProgressBar(String name) { super(name); this.unit = "it"; }
	public EasyProgressBar(String name, String unit) { super(name); this.unit = unit; }
	public void setUnit(String unit) { this.unit = unit; barTime = 0; }

	public void reset() {setTotal(0);}
	public void setUnlimited() {setTotal(-1);}
	public void setTotal(long total) {
		finish = 0;
		this.total = total;

		delta = 0;
		deltaTime = 0;
		barTime = 0;
	}
	public void addTotal(long total) {u.getAndAddLong(this, TOTAL_OFFSET, total);}

	public long getFinished() {return finish;}
	public long getTotal() {return total;}

	private double speed() { return deltaTime == 0 ? 0 : (double) delta / (System.currentTimeMillis() - deltaTime) * 1000; }
	public void increment() {increment(1);}
	public void increment(long count) {
		long prevFin = u.getAndAddLong(this, FINISH_OFFSET, count);
		long fin = prevFin + count;
		long tot = total;

		u.getAndAddLong(this, DELTA_OFFSET, count);

		long time = System.currentTimeMillis();
		long t = barTime;
		if (((tot >= 0 && fin < tot || prevFin >= tot) && time - t < BAR_DELAY) || !u.compareAndSwapLong(this, UPDATE_OFFSET, t, time)) return;

		if (time - deltaTime > AVG_SPEED_WINDOW) {
			delta = (long) speed();
			deltaTime = time - (AVG_SPEED_WINDOW / 2);
		}

		setPrefix(tot < 0
			? unit.equals("B") ? TextUtil.scaledNumber1024(fin) : String.valueOf(fin)
			: unit.equals("B") ? TextUtil.scaledNumber1024(fin)+"/"+TextUtil.scaledNumber1024(tot) : fin+"/"+tot);

		if (delta == 0 || fin > tot) {
			postfix = "--:--";
		} else {
			int eta = (int) Math.ceil((tot - fin) / speed());
			postfix = IOUtil.getSharedCharBuf().append(eta/3600).append(':').padNumber(eta/60%60, 2).append(':').padNumber(eta%60, 2).toString();
		}

		setProgress((double) fin / tot);
	}

	@Override
	protected int getPostFixWidth() {
		int width = Terminal.getStringWidth(unit)+9;
		width += Terminal.getStringWidth(postfix)+5;
		return width;
	}

	@Override
	protected void renderPostFix(CharList sb) {
		double speed = speed();
		String timeUnit = "s";
		if (speed < 1) {
			speed *= 60;
			timeUnit = "m";
		}
		if (speed < 1) {
			speed *= 60;
			timeUnit = "h";
		}
		if (speed < 1) {
			speed *= 24;
			timeUnit = "d";
		}

		sb.append(" \u001B[92m").append(speed < 1000 ? TextUtil.toFixed(speed, 2) : TextUtil.scaledNumber(Math.round(speed))).append(unit).append('/').append(timeUnit)
		  .append(" \u001B[93mETA: \u001B[94m").append(postfix);
	}

	@Override public void close() {setTotal(0);super.close();}
}