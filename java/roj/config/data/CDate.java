package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.config.serial.CVisitor;
import roj.text.ACalendar;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2021/7/7 0:43
 */
public final class CDate extends CLong {
	public CDate(long value) { super(value); }

	/**
	 * 2020-(0)1-10
	 */
	public static CDate valueOf(String val) {
		int[] buf = new int[3];
		int i = 0, off = 0, k = 0;
		while (i < val.length()) {
			if (val.charAt(i++) == '-') {
				buf[k++] = TextUtil.parseInt(val, off, i - off);
				off = i;
			}
		}
		long timestamp = (ACalendar.daySinceAD(buf[0], buf[1], buf[2], null) - ACalendar.GREGORIAN_OFFSET_DAY) * 86400000;
		return new CDate(timestamp);
	}

	@NotNull
	@Override
	public Type getType() { return Type.DATE; }
	@Override
	public boolean mayCastTo(Type o) { return o == Type.DATE; }
	@Override
	public void accept(CVisitor ser) { ser.valueDate(value); }
}