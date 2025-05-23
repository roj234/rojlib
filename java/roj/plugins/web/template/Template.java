package roj.plugins.web.template;

import roj.http.server.Request;
import roj.http.server.ResponseHeader;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/3/3 3:27
 */
public interface Template {
	default boolean isFast(Request req, ResponseHeader rh) { return false; }
	void render(Request req, CharList out, TemplateRenderer renderer);
}