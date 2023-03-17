package roj.config.data;

import roj.config.serial.CConsumer;
import roj.math.MathUtils;
import roj.text.ACalendar;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CDate extends CLong {
	public CDate(long value) {
		super(value);
	}

	/**
	 * 2020-(0)1-10
	 */
	public static CDate valueOf(String val) {
		int[] buf = new int[3];
		int i = 0, off = 0, k = 0;
		while (i < val.length()) {
			if (val.charAt(i++) == '-') {
				buf[k++] = MathUtils.parseInt(val, off, i - off, 10);
				off = i;
			}
		}
		long timestamp = (ACalendar.daySinceAD(buf[0], buf[1], buf[2], null) - ACalendar.GREGORIAN_OFFSET_DAY) * 86400000;
		return new CDate(timestamp);
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.DATE;
	}

	@Override
	public void forEachChild(CConsumer ser) {
		ser.valueDate(value);
	}
}
