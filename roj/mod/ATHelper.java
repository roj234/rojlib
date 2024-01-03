package roj.mod;

import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipOutput;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.util.TransformUtil;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.mod.Shared.BASE;

/**
 * Text AT Processor
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
final class ATHelper {
	public static final File AT_BACKUP_LIB = new File(BASE, "class/at_backup.zip_at");

	public static void close() {}

	public static void transform(ZipOutput zo, Map<String, Collection<String>> map, boolean forIDE) throws IOException {
		Shared.loadSrg2Mcp();

		List<ZipArchive> libraries = new SimpleList<>();
		for (File path : new File(BASE, "class").listFiles()) {
			String fn = path.getName().trim().toLowerCase();
			if (!fn.startsWith(DONT_LOAD_PREFIX) && (fn.endsWith(".jar") || fn.endsWith(".zip"))) libraries.add(new ZipArchive(path, ZipArchive.FLAG_BACKWARD_READ));
		}
		libraries.add(new ZipArchive(AT_BACKUP_LIB, 0));

		try {
			Map<String, Collection<String>> subClasses = new MyHashMap<>();

			for (String s : map.keySet()) {
				s = s.replace('.', '/');

				int i = s.lastIndexOf('$');
				if (i >= 0) {
					String par = s.substring(0, i);
					map.putIfAbsent(par.replace('/', '.'), Collections.emptyList());
					subClasses.computeIfAbsent(par+".class", Helpers.fnLinkedList()).add(s);
				}
			}

			MyHashSet<String> tmp = new MyHashSet<>();
			for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
				String name = entry.getKey().replace('.', '/') + ".class";

				tmp.clear();
				for (String s : entry.getValue()) {
					tmp.add(Shared.srg2mcp.getOrDefault(s, s));
				}

				InputStream source = null;
				for (int i = libraries.size()-1; i >= 0; i--) {
					try {
						source = libraries.get(i).getInput(name);
						if (source != null) break;
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}

				if (source == null) {
					CLIUtil.warning("无法找到 "+name);
				} else {
					ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(source));

					TransformUtil.makeAccessible(data, tmp);

					Collection<String> subclasses = subClasses.remove(name);
					if (subclasses != null) TransformUtil.makeSubclassAccessible(data, subclasses);

					if (!forIDE) TransformUtil.trimCode(data);

					data.parsed();
					zo.set(name, () -> Parser.toByteArrayShared(data));
				}
			}
		} finally {
			// AT_BACKUP
			libraries.remove(libraries.size()-1);
			for (int i = libraries.size() - 1; i >= 0; i--) {
				try {
					libraries.get(i).close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}