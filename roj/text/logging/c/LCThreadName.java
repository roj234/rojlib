package roj.text.logging.c;

import roj.text.CharList;
import roj.text.logging.LogContext;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 6:15
 */
public class LCThreadName implements LogComponent {
	public static final LCThreadName INSTANCE = new LCThreadName();

	@Override
	public void appendTo(LogContext ctx, Map<String, Object> tmp, CharList cl) {
		cl.append(Thread.currentThread().getName());
	}
}
