package roj.compiler.context;

import org.jetbrains.annotations.NotNull;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.collect.MyHashMap;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryFolder implements Library, Predicate<File> {
	public LibraryFolder(File file) {
		String path = file.getAbsolutePath();
		prefix = path.length();
		if (!path.endsWith("/")) prefix++;

		info = new MyHashMap<>();

		IOUtil.findAllFiles(file, this);
	}

	private int prefix;
	private final MyHashMap<String, Object> info;

	@Override
	public boolean test(File file) {
		if (file.getName().endsWith(".class")) {
			String path = file.getAbsolutePath();
			info.put(path.substring(prefix, path.length() - 6).replace('\\', '/'), file);
		}
		return false;
	}

	@Override
	@NotNull
	public Set<String> content() { return info.keySet(); }

	@Override
	public IClass get(CharSequence name) {
		Object o = info.get(name);
		if (o instanceof IClass) return (IClass) o;
		if (o == null) return null;
		try {
			ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream((File) o)));
			info.put(name.toString(), data);
			return data;
		} catch (IOException e) {
			e.printStackTrace();
			info.remove(name);
			return null;
		}
	}
}