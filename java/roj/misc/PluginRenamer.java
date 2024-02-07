package roj.misc;

import roj.config.ParseException;
import roj.config.YAMLParser;
import roj.config.data.CMapping;
import roj.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj234
 * @since 2021/2/26 10:52
 */
public class PluginRenamer {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("PluginRenamer <path>");
			System.out.println("用途：根据plugin.yml还原插件名称");
			return;
		}

		for (File file : new File(args[0]).listFiles()) {
			final String name = file.getName();
			if (file.isFile() && name.endsWith(".zip") || name.endsWith(".jar")) {
				CMapping map;
				try (ZipFile zf = new ZipFile(file)) {
					ZipEntry ze = zf.getEntry("plugin.yml");
					if (ze == null) continue;

					map = YAMLParser.parses(IOUtil.readUTF(zf.getInputStream(ze))).asMap();
				} catch (IOException | ParseException e) {
					System.out.println("In " + file.getName());
					e.printStackTrace();
					continue;
				}

				String prefix = map.getString("name") + '-' + map.getString("version");
				String ext = name.substring(name.lastIndexOf('.'));

				File newFile = new File(args[0], prefix+ext);
				int plus = 1;
				while (newFile.isFile()) {
					newFile = new File(args[0], prefix+"_"+plus+ext);
					plus++;
				}

				if (!file.renameTo(newFile)) {
					System.out.println("Fail rename " + file.getName() + " to " + newFile.getName());
				}
			}
		}
	}
}
