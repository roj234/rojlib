package roj.compiler.plugins.eval;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;

/**
 * @author Roj234
 * @since 2024/5/30 0030 3:34
 */
final class Sandbox extends ClassLoader {
	private MyHashSet<String> allowed = new MyHashSet<>("java.lang", "java.util", "java.util.regex", "java.util.function", "java.text", "roj.compiler.plugins.eval", "roj.compiler", "roj.text", "roj.config.data");
	private MyHashSet<String> disallowed = new MyHashSet<>("java.lang.Process", "java.lang.ProcessBuilder", "java.lang.Thread", "java.lang.ClassLoader");

	private final MyHashMap<String, byte[]> classData;

	public Sandbox(ClassLoader parent, MyHashMap<String, byte[]> data) {
		super(parent);
		classData = data;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			// First, check if the class has already been loaded
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				ClassNotFoundException cne;
				try {
					Class<?> type = getParent().loadClass(name);

					if (disallowed.contains(name)) throw new SecurityException("这个类显式的被拒绝使用"+name);

					String pkg = name.substring(0, name.lastIndexOf('.'));
					if (!allowed.contains(pkg)) throw new SecurityException("这个类不在白名单中"+name);

					return type;
				} catch (ClassNotFoundException e) {
					cne = e;
				}

				byte[] bytes = classData.get(name);
				if (bytes == null) throw cne;

				System.out.println("SCL define "+name);
				c = defineClass(name, bytes, 0, bytes.length);
			}

			if (resolve) resolveClass(c);
			return c;
		}
	}
}