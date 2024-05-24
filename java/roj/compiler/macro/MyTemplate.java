package roj.compiler.macro;

import roj.net.http.server.Request;
import roj.text.CharList;

import java.io.File;

/**
 * @author Roj234
 * @since 2024/5/24 0024 17:46
 */
public class MyTemplate implements Template {
	private File file;
	private long lastUpdate;
	private Template template;

	@Override
	public void apply(Request request, CharList out) {
		template.apply(request, out);
	}
}