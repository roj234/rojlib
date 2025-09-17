package roj.reflect;

import roj.asm.AsmCache;
import roj.asm.ClassDefinition;
import roj.collect.FlagSet;
import roj.collect.HashMap;

/**
 * @author Roj234
 * @since 2024/5/30 3:34
 */
public class Sandbox extends ClassLoader {
	public FlagSet restriction;
	public HashMap<String, byte[]> classBytes = new HashMap<>();

	private static final FlagSet DefaultPermits = new FlagSet(".");
	static {
		for (var packages : new String[] {"java.lang", "java.util", "java.util.regex", "java.util.function", "java.text", "roj.compiler", "roj.text", "roj.config.node"}) {
			DefaultPermits.add(packages, 1, false, false);
		}
		for (var classes : new String[] {"java.lang.Process", "java.lang.ProcessBuilder", "java.lang.Thread", "java.lang.ClassLoader"}) {
			DefaultPermits.add(classes, 0, false, false);
		}
	}

	public Sandbox(String name, ClassLoader parent) {this(name,parent,false);}
	public Sandbox(String name, ClassLoader parent, boolean withRestriction) {
		super(name, parent);
		if (withRestriction) {
			restriction = new FlagSet(".");
			restriction.addAll(DefaultPermits);
		}
	}

	@Override
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// First, check if the class has already been loaded
		Class<?> c = findLoadedClass(name);
		if (c == null) {
			ClassNotFoundException cne;
			try {
				Class<?> type = getParent().loadClass(name);
				if (restriction != null && restriction.get(name, 0) == 0) {
					String pkg = name.substring(0, name.lastIndexOf('.'));
					if (restriction.get(pkg, 0) == 0) throw new SecurityException(name+" 不在沙盒白名单中");
				}
				return type;
			} catch (ClassNotFoundException e) {
				cne = e;
			}

			byte[] bytes = classBytes.get(name);
			if (bytes == null) throw cne;

			c = defineClass(name, bytes, 0, bytes.length);
		}

		if (resolve) resolveClass(c);
		return c;
	}

	public void add(ClassDefinition node) {
		String name = node.name().replace('/', '.');
		classBytes.put(name, AsmCache.toByteArray(node));
	}
}