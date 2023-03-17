package ilib.gui.comp;

import ilib.gui.IGui;
import roj.math.MathUtils;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GNumberInput extends GTextInput {
	protected long val, min, max;

	public GNumberInput(IGui parent, int x, int y, int width, int height, int value) {
		super(parent, x, y, width, height, Integer.toString(value));
		this.val = value;
		this.min = Integer.MIN_VALUE;
		this.max = Integer.MAX_VALUE;
		this.maxLength = 12;
	}

	public GNumberInput(IGui parent, int x, int y, int width, int height, int value, int min, int max) {
		super(parent, x, y, width, height, Integer.toString(value));
		this.val = value;
		this.min = min;
		this.max = max;
		a();
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	protected void onChange(long value) {}

	@Override
	protected void onChange(String value) {
		val = Long.parseLong(value);
		onChange(val);

		super.onChange(value);
	}

	@Override
	protected boolean isValidText(String text) {
		if (TextUtil.isNumber(text) != 0) return false;

		long i = Long.parseLong(text);
		if (i < min) setText(Long.toString(min));
		else if (i > max) setText(Long.toString(max));
		return true;
	}

	@Override
	protected boolean isValidChar(char letter, int keyCode) {
		return letter == '.' || letter == '-' || Character.isDigit(letter);
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public int value() {
		return (int) val;
	}

	public long longValue() {
		return val;
	}

	public long min() {
		return min;
	}

	public void min(long min) {
		this.min = min;
		a();
	}

	public long max() {
		return max;
	}

	public void max(long max) {
		this.max = max;
		a();
	}

	private void a() {
		setMaxLength(Math.max(MathUtils.digitCount(min), MathUtils.digitCount(max)));
	}
}
