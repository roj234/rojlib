package roj.text.logging.c;

import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.logging.LogContext;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 6:15
 */
public class LCTime implements LogComponent {
	protected final String format;

	public static LCTime of(String format) {
		return new LCTime(format);
	}

	public LCTime(String format) {
		this.format = format;
	}

	@Override
	public void appendTo(LogContext ctx, Map<String, Object> tmp, CharList cl) {
		ACalendar cal = (ACalendar) tmp.get("LCAL");
		if (cal == null) tmp.put("LCAL", cal = new ACalendar());
		cal.formatDate(format, System.currentTimeMillis(), cl);
	}
}
