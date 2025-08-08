package roj.ui;

import org.jetbrains.annotations.NotNull;
import roj.concurrent.Timer;
import roj.concurrent.TimerTask;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.TextUtil;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2023/10/28 1:55
 */
public class EasyProgressBar extends ProgressBar {
	private String unit;
	private volatile long completed, total;
	private TimerTask updateTask;

	private volatile long delta;
	private long startTime, lastUpdateTime;
	private double avgSpeed;

	private static final double ALPHA = 0.2;
	private static final int AVG_SPEED_WINDOW = 500;
	private static final int INITIAL_THRESHOLD = 5;
	private static final double STALE_DECAY_FACTOR = 15000; // ms for exponential decay (e.g., halves roughly every ~10s)

	private static final long
			DELTA = Unaligned.fieldOffset(EasyProgressBar.class, "delta"),
			COMPLETED = Unaligned.fieldOffset(EasyProgressBar.class, "completed"),
			TOTAL = Unaligned.fieldOffset(EasyProgressBar.class, "total");

	public EasyProgressBar() { this(""); }
	public EasyProgressBar(String name) { super(name); this.unit = "it"; }
	public EasyProgressBar(String name, String unit) { super(name); this.unit = unit; }
	public void setUnit(String unit) { this.unit = unit; barTime = 0; }

	private void scheduledUpdate() {
		if (total == 0 || !Tty.hasBottomLine(line)) {
			if (updateTask != null)
				updateTask.cancel();
			return;
		}
		increment(0);
	}

	public void reset() {setTotal(0);}
	public void setUnlimited() {setTotal(-1);}
	public void setTotal(long total) {
		completed = 0;
		this.total = total;

		barTime = 0;
		startTime = lastUpdateTime = 0;
		avgSpeed = Double.NaN;

		isProgressUnknown = total < 0;
		if (isProgressUnknown) {
			prefix = "操作中";
			showCenterString = false;
		} else {
			showCenterString = true;
		}
	}
	public void addTotal(long total) {U.getAndAddLong(this, TOTAL, total);}

	public long getFinished() {return completed;}
	public long getTotal() {return total;}

	public void increment() {increment(1);}
	public void increment(long count) {
		long prevFin = U.getAndAddLong(this, COMPLETED, count);
		long fin = prevFin + count;
		long tot = total;

		long now = System.currentTimeMillis();
		if (count > 0) {
			U.getAndAddLong(this, DELTA, count);

			// Initialize startTime on first real increment
			if (startTime == 0) {
				startTime = now;
			}

			// Update speed only on real progress (count > 0) and if window elapsed
			if (now - lastUpdateTime > AVG_SPEED_WINDOW) {
				long deltaVal = U.getAndSetLong(this, DELTA, 0L);
				double timeDiffSec = (now - lastUpdateTime) / 1000.0;
				double instSpeed = (timeDiffSec > 0) ? deltaVal / timeDiffSec : 0.0;

				if (Double.isNaN(avgSpeed)) {
					avgSpeed = getAvgSpeed(now);
				} else {
					avgSpeed = ALPHA * instSpeed + (1 - ALPHA) * avgSpeed;
				}

				lastUpdateTime = now;
			}
		}

		long t = barTime;
		if (((tot >= 0 && fin < tot || prevFin >= tot) && now - t < BAR_DELAY) || !U.compareAndSetLong(this, UPDATE_OFFSET, t, now)) return;

		if (updateTask == null || updateTask.isCancelled())
			updateTask = Timer.getDefault().loop(this::scheduledUpdate, 1000);

		//prefix = "";

		setProgress(tot < 0 ? 1 : (double) fin / tot);
	}

	@Override
	protected @NotNull String getCenterString(double progress) {
		var fin = completed;
		var tot = total;
		return (tot < 0
				? unit.equals("B") ? TextUtil.scaledNumber1024(fin) : String.valueOf(fin)
				: unit.equals("B") ? TextUtil.scaledNumber1024(fin)+"/"+TextUtil.scaledNumber1024(tot) : fin+"/"+tot);
	}

	@Override
	protected @NotNull String getCenterStringColor(boolean filled) {
		return String.valueOf(filled ? Tty.BLUE : Tty.CYAN+Tty.HIGHLIGHT);
	}

	private double getAvgSpeed(long now) {
		// Handle initial case or no speed yet: use total elapsed / completed for base speed
		if (Double.isNaN(avgSpeed) || avgSpeed <= 0) {
			if (completed > 0 && startTime > 0) {
				double elapsedSec = (now - startTime) / 1000.0;
				if (elapsedSec <= 0) return 0.0;
				double baseSpeed = completed / elapsedSec; // items per second
				// Apply conservative factor for small samples (slower estimated speed for longer ETA)
				double conservativeFactor = 1.0;
				if (completed <= INITIAL_THRESHOLD) {
					conservativeFactor = 1.0 / (1.0 + (INITIAL_THRESHOLD - completed) * 0.2);
				}
				return baseSpeed * conservativeFactor * getStaleDecayFactor(now);
			}
			return 0.0;
		}

		// For established avgSpeed, apply stale decay if no recent updates
		return avgSpeed * getStaleDecayFactor(now);
	}

	private double getStaleDecayFactor(long now) {
		long staleMs = now - lastUpdateTime;
		if (staleMs <= AVG_SPEED_WINDOW) {
			return 1.0; // No decay if recent update
		}
		// Exponential decay for stale periods: speed decreases over time without progress
		// Ensures ETA increases gradually when stuck, making it feel dynamic
		double decayRate = staleMs / STALE_DECAY_FACTOR;
		return Math.exp(-decayRate);
	}

	@Override
	protected void renderRight(CharList sb, double progress) {
		long now = System.currentTimeMillis();
		double speed = getAvgSpeed(now);

		String timeUnit = "s";
		double displaySpeed = speed;
		if (displaySpeed < 1) {
			displaySpeed = speed * 60;
			timeUnit = "m";
		}
		if (displaySpeed < 1) {
			displaySpeed *= 60;
			timeUnit = "h";
		}
		if (displaySpeed < 1) {
			displaySpeed *= 24;
			timeUnit = "d";
		}

		sb.append(" \u001B[92m").append(
				displaySpeed < 1000
				? TextUtil.toFixed(displaySpeed, 2)
				: TextUtil.scaledNumber(Math.round(displaySpeed))
		).append(unit).append('/').append(timeUnit);

		if (total >= 0) {
			sb.append(" \u001B[93mETA: \u001B[94m");

			long remaining = total - completed;
			double etaSec = remaining / speed;
			if (etaSec > 1000000) sb.append('∞');
			else if (etaSec > 86400) sb.append(">1d");
			else if (etaSec < 1) sb.append("<1s");
			else {
				int eta = (int) Math.ceil(etaSec);
				if (eta >= 3600) {
					sb.append(eta / 3600).append('h').append(eta / 60 % 60).append('m');
				} else if (eta >= 60) {
					sb.append(eta / 60 % 60).append('m').append(eta % 60).append('s');
				} else {
					sb.append(eta % 60).append('s');
				}
			}
		}
	}

	@Override
	public void end() {
		setTotal(0);
		super.end();
	}
}