package roj.net.http.srv.error;

import roj.io.IOUtil;
import roj.text.Template;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/2/11 0011 1:14
 */
public class GreatErrorPage {
	static Template template;
	static {
		try {
			template = Template.compile(IOUtil.readUTF("META-INF/html/system_error.html"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
