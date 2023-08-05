package roj.dev;

import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.data.Document;
import roj.config.data.Element;
import roj.config.data.Node;
import roj.text.TextWriter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Automatic add/remove IDEA source roots
 *
 * @author solo6975
 * @since 2021/7/23 10:57
 */
public final class IDEAHelper {
	public static void config(File imlPath, String projName, boolean remove) throws IOException, ParseException {
		String basePath = "file://$MODULE_DIR$/projects/" + projName + "/";

		Document xml = new XMLParser().parseToXml(imlPath,0);
		Element content = xml.querySelector("/module/component[name=\"NewModuleRootManager\"]/content").asElement();
		List<Node> sourceUrls = content.children();
		for (int i = 0; i < sourceUrls.size(); i++) {
			Element element = sourceUrls.get(i).asElement();
			if (element.attr("url").asString().startsWith(basePath)) {
				if (remove) sourceUrls.remove(i--);
				else return; // already have
			}
		}

		if (!remove) {
			Element el = xml.createElement("sourceFolder")
				.attr("isTestSource", "false")
				.attr("url", basePath + "java");
			content.add(el);
			el = xml.createElement("sourceFolder")
				.attr("type", "java-resource")
				.attr("url", basePath + "resources");
			content.add(el);
		}

		try (TextWriter sb = TextWriter.to(imlPath)) {
			xml.toCompatXML(sb);
		}
	}
}
