package roj.text.logging.c;

import roj.text.CharList;
import roj.text.DateParser;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 6:15
 */
public class LCTime implements LogComponent {
	protected final String format;
	public static LCTime of(String format) { return new LCTime(format); }
	public LCTime(String format) { this.format = format; }

	@Override
	public void accept(Map<String, Object> tmp, CharList sb) {
		DateParser cal = (DateParser) tmp.get("LCAL");
		if (cal == null) tmp.put("LCAL", cal = DateParser.local());
		cal.format(format, System.currentTimeMillis(), sb);
	}
}
