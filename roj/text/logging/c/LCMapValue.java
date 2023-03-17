package roj.text.logging.c;

import roj.text.CharList;
import roj.text.logging.LogContext;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 6:15
 */
public class LCMapValue implements LogComponent {
	public static final LCMapValue LEVEL = of("LEVEL");
	public static final LCMapValue NAME = of("NAME");

	private final String key;

	public static LCMapValue of(String key) {
		return new LCMapValue(key);
	}

	public LCMapValue(String key) {this.key = key;}

	@Override
	public void appendTo(LogContext ctx, Map<String, Object> tmp, CharList cl) {
		cl.append(tmp.get(key));
	}
}
