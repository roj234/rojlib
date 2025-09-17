package roj.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;
import roj.text.CharList;
import roj.text.TextUtil;

import java.lang.invoke.VarHandle;

/**
 * @author Roj234
 * @since 2022/11/19 3:33
 */
@FastVarHandle
public class ProgressBar implements AutoCloseable {
	protected final CharList line = new CharList();
	protected boolean isProgressUnknown, showCenterString;

	protected static final int BAR_DELAY = 40;
	protected volatile long barTime;
	protected static final VarHandle BAR_TIME = Handles.lookup().findVarHandle(ProgressBar.class, "barTime", long.class);

	private boolean closed;

	protected String name, prefix;
	public ProgressBar() { this.name = ""; }
	public ProgressBar(String name) { this.name = name; }
	public void setName(String name) { this.name = name; barTime = 0; }
	public void setPrefix(String prefix) { this.prefix = prefix; barTime = 0; }
	public void setShowCenterString(boolean showCenterString) {this.showCenterString = showCenterString;}
	public void setProgressUnknown(boolean progressUnknown) {isProgressUnknown = progressUnknown;}

	protected void render(CharList b) { if (!closed) Tty.renderBottomLine(b); }
	protected void renderRight(CharList sb, double progress) {}

	public void setTitle(String text) {
		line.clear();
		render(line.append(text));
	}

	public boolean setProgressWithUpdateLimit(double progress) {
		long time = System.currentTimeMillis();
		long t = barTime;
		if (time - t < BAR_DELAY || !BAR_TIME.compareAndSet(this, t, time)) return false;
		setProgress(progress);
		return true;
	}

	private int spinnerFrame, prevAuxiliaryWidth;

	public void setProgress(@Range(from = 0, to = 1) double progress) {
		progress = MathUtils.clamp(progress, 0, 1);

		line.clear();
		CharList b = (name == null ? line : line.append(Tty.reset).append(name).append(": ")).append("\u001B[96m");

		if (prefix == null) b.append(TextUtil.toFixedLength(progress * 100, 4)).append('%');
		else b.append(prefix);

		// Pre-compute postfix to include its width in total prefix calculation for stability
		var postfix = IOUtil.getSharedCharBuf();
		renderRight(postfix, progress);
		int baseWidth = Tty.getStringWidth(b);
		int postfixWidth = Tty.getStringWidth(postfix);
		int auxiliaryWidth = baseWidth + postfixWidth;

		// Stabilize total prefix width to avoid jitter from postfix changes
		int pad = 0;
		if (auxiliaryWidth > prevAuxiliaryWidth || auxiliaryWidth < prevAuxiliaryWidth - 5) {
			prevAuxiliaryWidth = auxiliaryWidth;
		} else {
			pad = prevAuxiliaryWidth - auxiliaryWidth;
			auxiliaryWidth = prevAuxiliaryWidth;
		}

		// Pad the base part to maintain stable total prefix width
		b.padEnd(' ', pad - postfixWidth);  // Adjust pad for base, postfix added later

		int barWidth = Tty.getColumns() - auxiliaryWidth - 4;  // 4 for " [] "
		if (spinnerFrame >= 0) {
			// -1 for disable spinner
			barWidth -= 2;
		}

		if (barWidth < 10) {
			if (prefix != null && !isProgressUnknown) {
				b.append(' ').append(TextUtil.toFixed(progress * 100, 1)).append('%');
			}
		} else {
			b.append(Tty.reset+" ");

			// Insert percentage in the middle if feasible (rich terminal)
			if (showCenterString && !isProgressUnknown && Tty.IS_RICH) {
				String percentStr = getCenterString(progress);

				int percentLen = Tty.getStringWidth(percentStr);
				int leftLen = (barWidth - percentLen) / 2;
				int rightLen = barWidth - percentLen - leftLen;

				// white background (no unicode blocks, they're all spaces!)
				b.append("\u001B["+(getCenterStringColor(true))+";"+(Tty.WHITE+10)+"m");

				int index = b.length();

				b.padEnd(' ', leftLen).append(percentStr).padEnd(' ', rightLen)
				// black background
				 .insert(index+(int) Math.round(progress * (b.length()-index)), "\u001B["+(getCenterStringColor(false))+";"+(Tty.BLACK+10)+"m")
				// reset
				 .append(Tty.reset);
			} else {
				if (Tty.isFullUnicode()) {
					double dFill = progress * barWidth;
					int fill = (int) Math.floor(dFill);

					var bar = new CharList();
					if (fill == 0) {
						bar.append(" ▏▎▍▌▋▊▉█".charAt((int) Math.floor(dFill * (9 - 1))))
								.padEnd(' ', barWidth-1);
					} else {
						bar.padEnd('█', fill)
								.append("▌▋▊▉█".charAt((int) Math.round((dFill - fill) * (5 - 1))))
								.padEnd(' ', barWidth-fill-1);
					}
					if (!isProgressUnknown || !Tty.IS_RICH) b.append(bar);
					else Tty.TextEffect.rainbow(bar.toStringAndFree(), b);
				} else {
					int fill = (int) Math.round(progress * barWidth);

					b.append('[');
					if (!isProgressUnknown || !Tty.IS_RICH) b.padEnd('=', fill);
					else Tty.TextEffect.rainbow("=".repeat(fill), b);
					b.padEnd(' ', barWidth - fill).append(Tty.reset).append(']');
				}
			}
		}

		if (spinnerFrame >= 0) {
			String spinner = Tty.isFullUnicode() ? "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏" : "|/-\\";
			b.append("\u001B[93m ").append(spinner.charAt(spinnerFrame = (spinnerFrame + 1) % spinner.length()));
		}

		render(b.append(postfix).append(Tty.reset));
	}

	@NotNull
	protected String getCenterStringColor(boolean filled) {return String.valueOf(filled ? Tty.BLACK : Tty.WHITE);}
	@NotNull
	protected String getCenterString(double progress) {return TextUtil.toFixed(progress * 100, 2)+'%';}

	@Override public void close() { end(); closed = true; }
	public void end() { Tty.removeBottomLine(line); }
	public final void end(String message) { end(message, Tty.GREEN); }
	public final void end(String message, int color) {
		end();

		line.clear();
		System.out.println(line
				.append("\u001b[2K").append(name).append(": ")
				.append("\u001B[").append(color + Tty.HIGHLIGHT).append('m')
				.append(message)
				.append(Tty.reset));
	}
}