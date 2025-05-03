package roj.ui;

import roj.math.MathUtils;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.TextUtil;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2022/11/19 3:33
 */
public class ProgressBar implements AutoCloseable {
	protected final CharList batch = new CharList();
	protected boolean animation = true;

	protected static final int BAR_DELAY = 40;
	protected volatile long barTime;
	protected static final long UPDATE_OFFSET = ReflectionUtils.fieldOffset(ProgressBar.class, "barTime");

	protected String name, prefix;
	public ProgressBar(String name) {this.name = name;}
	public void setName(String name) {this.name = name;barTime = 0;}
	public void setPrefix(String prefix) {this.prefix = prefix;barTime = 0;}
	public void setAnimation(boolean animation) {this.animation = animation;}

	protected int getPostFixWidth() {return 0;}
	protected void renderPostFix(CharList sb) {}
	protected void render(CharList b) {
		Terminal.renderBottomLine(b);}
	public void _forceUpdate() {render(batch);}

	public void set(String text) {
		batch.clear();
		render(batch.append(text));
	}

	public boolean setProgressWithDelay(double progress) {
		long time = System.currentTimeMillis();
		long t = barTime;
		if (time - t < BAR_DELAY || !U.compareAndSwapLong(this, UPDATE_OFFSET, t, time)) return false;
		setProgress(progress);
		return true;
	}

	private static final String TIMER = "|/-\\";
	private int timerId, prevWidth;
	public synchronized void setProgress(double progress) {
		progress = MathUtils.clamp(progress, 0, 1);

		batch.clear();
		CharList b = (name == null ? batch : batch.append("\u001b[0m").append(name).append(": ")).append("\u001B[96m");

		if (prefix == null) b.append(TextUtil.toFixedLength(progress*100, 4)).append('%');
		else b.append(prefix);

		int width = Terminal.getStringWidth(b);
		int pad = 0;
		if (width > prevWidth || width < prevWidth - 5) {
			prevWidth = width;
		} else {
			pad = prevWidth - width;
			width = prevWidth;
		}

		width += getPostFixWidth();

		int progressWidth = Terminal.windowWidth - width - 7;
		if (progressWidth < 10) progressWidth = 10;

		int tx = (int) Math.round(progress * progressWidth);

		b.padEnd(' ', pad).append("\u001B[0m [");
		if (animation) Terminal.Color.sonic("=".repeat(tx), b);
		else b.padEnd('=', tx);
		b.padEnd(' ', progressWidth - tx).append("]\u001B[93m ")
		 .append(TIMER.charAt(timerId = (timerId+1) & 3));

		renderPostFix(b);
		render(b.append("\u001B[0m"));
	}

	@Override public void close() {
		Terminal.removeBottomLine(batch);}
	public final void end() {close();}
	public final void end(String message) {end(message, Terminal.GREEN);}
	public void end(String message, int color) {
		close();

		batch.clear();
		System.out.println(batch
			.append("\u001b[2K").append(name).append(": ")
			.append("\u001B[").append(color+ Terminal.HIGHLIGHT).append('m')
			.append(message)
			.append("\u001B[0m"));
	}
}