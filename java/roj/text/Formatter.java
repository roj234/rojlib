package roj.text;

import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/29 0029 2:16
 */
public interface Formatter {
	CharList format(Map<String, ?> env, CharList sb);
}