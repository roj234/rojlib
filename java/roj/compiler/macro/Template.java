package roj.compiler.macro;

import roj.net.http.server.Request;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/3/3 0003 3:27
 */
public interface Template {
	void apply(Request request, CharList out);
}