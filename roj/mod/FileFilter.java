package roj.mod;

import roj.config.JSONParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.io.PushbackInputStream;
import roj.text.TextReader;
import roj.util.ArrayCache;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static roj.mod.Shared.BASE;
import static roj.mod.Shared.CONFIG;

/**
 * @author Roj233
 * @since 2021/7/11 14:23
 */
public class FileFilter implements Predicate<File> {
	public static final int F_RES_TIME = 0, F_SRC_ANNO = 1, F_SRC_TIME = 2, F_RES = 3;

	public static final FileFilter INST = new FileFilter();

	FileFilter() {}

	static final int buffer = CONFIG.getInteger("AT查找缓冲区大小");
	static final List<CmtATEntry> cmtEntries = new ArrayList<>();

	private long stamp;
	private int mode;

	@Override
	public boolean test(File file) {
		switch (mode) {
			case F_RES_TIME:
				return file.lastModified() > stamp;
			case F_SRC_TIME:
				return file.getName().endsWith(".java") && file.lastModified() > stamp;
			case F_SRC_ANNO: {
				boolean is = file.getName().endsWith(".java");
				if (is) checkATComments(file);
				return is;
			}
			case F_RES:
				return true;
		}

		return false;
	}

	public FileFilter reset(long stamp, int enable) {
		this.stamp = stamp;
		this.mode = enable;
		cmtEntries.clear();
		return this;
	}


	// region OpenAny Finder

	public static boolean checkATComments(File file) {
		if (buffer == 0) return false;

		byte[] b = ArrayCache.getDefaultCache().getByteArray(buffer, false);
		try (FileInputStream in = new FileInputStream(file)) {
			int len = in.read(b);

			for (int i = 0; i < len; i++) {
				if (b[i] != '/' || !regionMatches(b, i, COMMENT_BEGIN)) continue;

				PushbackInputStream in1 = new PushbackInputStream(in);
				in1.setBuffer(b, i+COMMENT_BEGIN.length(), len);

				CList list;
				try (TextReader sr = new TextReader(in1, Shared.project.charset)) {
				 	list = new JSONParser().parse(sr, JSONParser.NO_DUPLICATE_KEY | JSONParser.NO_EOF | JSONParser.UNESCAPED_SINGLE_QUOTE).asList();
				}

				for (int j = 0; j < list.size(); j++) {
					CEntry e1 = list.get(j);
					if (e1.getType() != Type.LIST) j = list.size();
					CList list1 = e1.getType() == Type.LIST ? e1.asList() : list;

					CmtATEntry entry = new CmtATEntry();
					entry.clazz = list1.get(0).asString();
					entry.value = list1.get(1).asList().asStringList();
					entry.compile = list1.size() > 2 && list1.get(2).asBool();

					synchronized (cmtEntries) {
						cmtEntries.add(entry);
					}
				}
				return true;
			}
		} catch (Exception e) {
			System.out.println("文件: " + file.getPath().substring(BASE.getAbsolutePath().length()+1));
			e.printStackTrace();
		} finally {
			ArrayCache.getDefaultCache().putArray(b);
		}

		return false;
	}

	private static final String COMMENT_BEGIN = "//!!AT ";

	private static boolean regionMatches(byte[] list, int index, CharSequence seq) {
		if (index + seq.length() > list.length) return false;

		int j = 0;
		for (int i = index; j < seq.length(); i++, j++) {
			if (list[i] != seq.charAt(j)) return false;
		}

		return true;
	}

	static final class CmtATEntry {
		String clazz;
		List<String> value;
		boolean compile;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			CmtATEntry entry = (CmtATEntry) o;

			if (compile != entry.compile) return false;
			if (!clazz.equals(entry.clazz)) return false;
			return value.equals(entry.value);
		}

		@Override
		public int hashCode() {
			int result = clazz.hashCode();
			result = 31 * result + value.hashCode();
			result = 31 * result + (compile ? 1 : 0);
			return result;
		}
	}

	// endregion
}
