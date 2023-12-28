package roj.reflect;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.ProtectionDomain;

/**
 * @author Roj234
 * @since 2021/6/16 1:31
 */
public class ClassDefiner extends ClassLoader {
	public static ClassDefiner getFor(Class<?> c) { return new ClassDefiner(getParent(c)); }
	public static ClassDefiner INSTANCE = getFor(ClassDefiner.class);

	private static final H def;
	private interface H {
		Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd);
		Class<?> findLoadedClass(ClassLoader loader, String name);
	}
	static {
		ClassLoader.registerAsParallelCapable();

		AsmShared.local().setLevel(true);
		try {
			def = DirectAccessor.builder(H.class).delegate(ClassLoader.class, new String[] {"defineClass", "findLoadedClass"}).build();
		} finally {
			AsmShared.local().setLevel(false);
		}
	}

	public static boolean debug = System.getProperty("roj.reflect.debugClass") != null;
	protected static void dumpClass(String name, ByteList buf) {
		if (debug) {
			File f = new File("./ClassDefiner_dump");
			f.mkdir();
			try (FileOutputStream fos = new FileOutputStream(new File(f, name+".class"))) {
				buf.writeToStream(fos);
			} catch (IOException ignored) {}
		}
	}

	public static Class<?> findLoadedClass(ClassLoader loader, String name) { return def.findLoadedClass(loader, name); }
	public static Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd) { return def.defineClass(loader, name, b, off, len, pd); }

	public ClassDefiner(ClassLoader parent) { super(parent); }

	public final Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError { return defineClass(name, IOUtil.SharedCoder.get().wrap(bytes)); }
	public final Class<?> defineClass(IClass data) { return defineClass(null, Parser.toByteArrayShared(data)); }
	public Class<?> defineClass(String name, ByteList buf) throws ClassFormatError {
		dumpClass(name, buf);

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) buf = sm.checkDefineClass(name, buf);

		int off = buf.arrayOffset()+buf.rIndex;
		try{
			if (def != null) {
				ClassLoader p = getParent();
				return def.defineClass(p, name, buf.list, off, buf.readableBytes(), p.getClass().getProtectionDomain());
			}

			return defineClass(name, buf.list, off, buf.readableBytes(), getClass().getProtectionDomain());
		} finally {
			buf.rIndex = buf.wIndex();
		}
	}

	private static ClassLoader getParent(Class<?> type) {
		ClassLoader parent = type.getClassLoader();
		if (parent == null) parent = ClassLoader.getSystemClassLoader();
		return parent;
	}
}