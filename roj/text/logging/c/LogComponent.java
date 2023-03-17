package roj.text.logging.c;

import roj.text.CharList;
import roj.text.logging.LogContext;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 5:43
 */
public interface LogComponent {
	void appendTo(LogContext ctx, Map<String, Object> tmp, CharList cl);
}
