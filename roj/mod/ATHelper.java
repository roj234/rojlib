package roj.mod;

import roj.archive.zip.ZEntry;
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
import static roj.mod.Shared.TMP_DIR;

/**
 * Text AT Processor
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
final class ATHelper {
	private static final List<ATHelper> libraries = new SimpleList<>();

	private static final ATHelper AT_BACKUP_LIB = new ATHelper(new File(BASE, "class/at_backup.zip_at"));
	static ZipArchive getBackupFile() throws IOException {
		AT_BACKUP_LIB.open();
		return AT_BACKUP_LIB.zf;
	}

	public static File getJarName(String name) {
		return new File(TMP_DIR, "at-" + name.hashCode() + ".jar");
	}

	public static void close() {
		for (int i = 0; i < libraries.size(); i++) {
			try {
				libraries.get(i).close1();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		libraries.clear();
	}

	public static void makeATJar(String name, Map<String, Collection<String>> map) throws IOException {
		ZipOutput zo = new ZipOutput(getJarName(name));
		zo.setCompress(true);
		try {
			zo.begin(true);
			transform(zo, map, false);
		} finally {
			zo.close();
		}
	}
	public static void transform(ZipOutput zo, Map<String, Collection<String>> map, boolean forIDE) throws IOException {
		Shared.loadSrg2Mcp();

		if (libraries.isEmpty()) {
			for (File path : new File(BASE, "class").listFiles()) {
				String fn = path.getName().trim().toLowerCase();
				if (!fn.startsWith(DONT_LOAD_PREFIX) && (fn.endsWith(".jar") || fn.endsWith(".zip"))) libraries.add(new ATHelper(path));
			}
		} else {
			for (int i = libraries.size()-1; i >= 0; i--) {
				try {
					if (!libraries.get(i).open()) libraries.remove(i);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		libraries.add(AT_BACKUP_LIB);

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

				InputStream source = getBytecode(name);
				if (source == null) {
					CLIUtil.warning("无法找到 " + name);
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
					ZipArchive zf1 = libraries.get(i).zf;
					if (zf1 == null) libraries.remove(i);
					else zf1.closeFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	private static InputStream getBytecode(String name) {
		for (int i = libraries.size()-1; i >= 0; i--) {
			try {
				InputStream in = libraries.get(i).getStream(name);
				if (in != null) return in;
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private final File file;
	private ZipArchive zf;
	private long lastModify;

	private ATHelper(File file) {
		this.file = file;
	}

	private InputStream getStream(String name) throws IOException {
		if (zf == null && !open()) return null;
		ZEntry entry = zf.getEntries().get(name);
		if (entry != null) {
			if (open()) return zf.getStream(entry);
		}
		return null;
	}

	private boolean open() throws IOException {
		if (this == AT_BACKUP_LIB) {
			if (zf == null) zf = new ZipArchive(file, 0);
			else zf.reopen();
			return true;
		}

		if (!file.isFile()) {
			close1();
			return false;
		} else {
			long lastMod = file.lastModified();

			if (zf != null) {
				zf.reopen();

				if (lastMod != lastModify) {
					zf.reload();

					lastModify = lastMod;
				}
			} else {
				zf = new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ);
				lastModify = lastMod;
			}
		}

		return true;
	}

	private void close1() throws IOException {
		if (zf != null) {
			zf.close();
			zf = null;
		}
	}
}