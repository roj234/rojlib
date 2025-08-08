package roj.unleaked;

import roj.archive.zip.ZipArchive;
import roj.asmx.Context;
import roj.collect.HashSet;
import roj.collect.TrieTreeSet;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2024/8/14 13:17
 */
final class Sandbox extends ClassLoader {
	private static final TrieTreeSet allowed = new TrieTreeSet("roj.unleaked", "java.io.InputStream", "java.io.ByteArrayOutputStream", "java.nio.charset", "java.lang", "java.util", "java.util.regex", "java.util.function", "java.text", "java.time", "java.lang.invoke", "javax.crypto", "java.security");
	private static final HashSet<String> disallowed = new HashSet<>("java.lang.Process", "java.lang.ProcessBuilder", "java.lang.Thread");

	private final ZipArchive za;
	Sandbox(ZipArchive archive) {za = archive;}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			// First, check if the class has already been loaded
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				try {
					Class<?> type = getParent().loadClass(name);

					if (disallowed.contains(name)) throw new Error("这个类显式的被拒绝使用" + name);
					if (!allowed.strStartsWithThis(name)) new Error("这个类不在白名单中" + name).printStackTrace();

					return type;
				} catch (ClassNotFoundException e) {
					// ClassNotFoundException thrown if class not found
					// from the non-null parent class loader
				}

				byte[] bytes;
				try {
					bytes = za.get(name.replace('.', '/') + ".class");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				if (bytes == null) throw new ClassNotFoundException(name);
				var ctx = new Context(name, bytes);
				if (UnleakMain.instance.transform(name, ctx)) {
					bytes = ctx.getClassBytes().toByteArray();
				}

				c = defineClass(name, bytes, 0, bytes.length);
			}

			if (resolve) resolveClass(c);
			return c;
		}
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			za.put(name, null);
			return za.getStream(name);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
