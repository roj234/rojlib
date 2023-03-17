package roj.reflect;

import roj.asm.AsmShared;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/16 1:31
 */
public class ClassDefiner extends ClassLoader {
	private interface H {
		Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd);
		List<Class<?>> getClasses(ClassLoader loader);
		Class<?> findLoadedClass(ClassLoader loader, String name);
	}

	private static final ClassLoader SELF_LOADER = getParent(ClassDefiner.class);
	public static final ClassDefiner INSTANCE = new ClassDefiner(SELF_LOADER);

	public static ClassDefiner getFor(ClassLoader loader) {
		return new ClassDefiner(getParent(loader.getClass()));
	}

	public static boolean debug = System.getProperty("roj.reflect.debugClass") != null;

	private static final H def;
	private static final int flag;

	static {
		ClassLoader.registerAsParallelCapable();

		H h = null;
		int f = 0;

		AsmShared.local().setLevel(true);
		try {
			DirectAccessor<H> b = DirectAccessor.builder(H.class);
			try {
				b.delegate(ClassLoader.class, new String[] {"defineClass", "findLoadedClass"});
				f |= 1;
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				b.access(ClassLoader.class, "classes", "getClasses", null);
				f |= 2;
			} catch (Exception ignored) {}
			h = b.build();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			AsmShared.local().setLevel(false);
		}

		def = h;
		flag = f;
	}

	public static Class<?> findLoadedClass(ClassLoader loader, String name) {
		return def.findLoadedClass(loader, name);
	}

	public ClassDefiner(ClassLoader parent) {
		super(parent);
	}

	public Class<?> loadClass(String className, boolean init) throws ClassNotFoundException {
		return super.loadClass(className, init);
	}

	public Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError {
		return defineClassC(name, bytes, 0, bytes.length);
	}

	public Class<?> defineClassC(String name, ByteList data) throws ClassFormatError {
		try {
			return defineClassC(name, data.list, data.arrayOffset() + data.rIndex, data.wIndex());
		} finally {
			data.rIndex = data.wIndex();
		}
	}

	public Class<?> defineClassC(String name, byte[] bytes, int off, int len) throws ClassFormatError {
		if (debug) {
			File f = new File("./class_Definer_out");
			f.mkdir();
			try (FileOutputStream fos = new FileOutputStream(new File(f, name + ".class"))) {
				fos.write(bytes, off, len);
			} catch (IOException ignored) {}
		}

		if ((flag&2)!=0) def.getClasses(this).clear();

		try {
			if (def != null) {
				// 使用同样的加载器加载，保证Access
				return def.defineClass(getParent(), name, bytes, off, len, getParent().getClass().getProtectionDomain());
			}
		} catch (Exception e) {
			e.printStackTrace();
			// 使用自己加载（这样会没有protected的权限!）
		}

		return defineClass(name, bytes, off, len, getClass().getProtectionDomain());
	}

	private static ClassLoader getParent(Class<?> type) {
		ClassLoader parent = type.getClassLoader();
		if (parent == null) parent = ClassLoader.getSystemClassLoader();
		return parent;
	}
}
