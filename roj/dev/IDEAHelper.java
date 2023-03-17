package roj.dev;

import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.data.XElement;
import roj.config.data.XEntry;
import roj.config.data.XHeader;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
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

		XHeader header = XMLParser.parses(IOUtil.readUTF(imlPath));
		XElement content = header.getXS("module.component[name=\"NewModuleRootManager\"].content").get(0).asElement();
		List<XEntry> sourceUrls = content.children();
		for (int i = 0; i < sourceUrls.size(); i++) {
			XElement element = sourceUrls.get(i).asElement();
			if (element.attr("url").asString().startsWith(basePath)) {
				if (remove) {sourceUrls.remove(i--);} else return;
			}
		}

		if (!remove) {
			XElement el = new XElement("sourceFolder");
			el.put("isTestSource", "false");
			el.put("url", basePath + "java");
			content.add(el);
			el = new XElement("sourceFolder");
			el.put("type", "java-resource");
			el.put("url", basePath + "resources");
			content.add(el);
		}

		try (FileOutputStream fos = new FileOutputStream(imlPath)) {
			IOUtil.SharedCoder.get().encodeTo(header.toCompatXML(), fos);
		}
	}
}
