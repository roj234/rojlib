package roj.plugins.web.template;

import roj.http.server.Request;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/3/3 3:27
 */
public interface Template {
	default boolean isFast() { return false; }
	void render(Request request, CharList response, TemplateRenderer renderer);
}