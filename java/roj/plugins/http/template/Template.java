package roj.plugins.http.template;

import roj.net.http.server.Request;
import roj.net.http.server.ResponseHeader;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/3/3 0003 3:27
 */
public interface Template {
	default boolean isFast(Request req, ResponseHeader rh) { return false; }
	void render(Request req, CharList out, TemplateRenderer renderer);
}