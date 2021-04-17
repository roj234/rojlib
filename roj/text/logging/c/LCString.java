package roj.text.logging.c;

import roj.text.CharList;
import roj.text.logging.LogContext;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 6:15
 */
public class LCString implements LogComponent {
	private final String data;

	public static LCString of(String str) {
		return new LCString(str);
	}

	public LCString(String data) {this.data = data;}

	@Override
	public void appendTo(LogContext ctx, Map<String, Object> tmp, CharList cl) {
		cl.append(data);
	}
}
