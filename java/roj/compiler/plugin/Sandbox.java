package roj.compiler.plugin;

import roj.collect.MyHashMap;
import roj.util.PermissionSet;

/**
 * @author Roj234
 * @since 2024/5/30 3:34
 */
final class Sandbox extends ClassLoader {
	final PermissionSet permits = new PermissionSet(".");
	final MyHashMap<String, byte[]> classBytes = new MyHashMap<>();

	public Sandbox(ClassLoader parent) {super(parent);}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			// First, check if the class has already been loaded
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				ClassNotFoundException cne;
				try {
					Class<?> type = getParent().loadClass(name);
					if (permits.get(name, 0) == 0) {
						String pkg = name.substring(0, name.lastIndexOf('.'));
						if (permits.get(pkg, 0) == 0) throw new SecurityException(name+" 不在沙盒白名单中");
					}
					return type;
				} catch (ClassNotFoundException e) {
					cne = e;
				}

				byte[] bytes = classBytes.get(name);
				if (bytes == null) throw cne;

				System.out.println("SCL define "+name);
				c = defineClass(name, bytes, 0, bytes.length);
			}

			if (resolve) resolveClass(c);
			return c;
		}
	}
}