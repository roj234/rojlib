package roj.reflect;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.security.ProtectionDomain;

/**
 * @author Roj234
 * @since 2021/6/16 1:31
 */
public class ClassDefiner extends ClassLoader {
	public static ClassDefiner getFor(Class<?> c) { return new ClassDefiner(getParent(c)); }
	public static final ClassDefiner INSTANCE = getFor(ClassDefiner.class);

	private static final H def;
	private interface H {
		Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd);
		Class<?> findLoadedClass(ClassLoader loader, String name);
	}
	static {
		ClassLoader.registerAsParallelCapable();

		AsmShared.local().setUnbuffered(true);
		try {
			def = DirectAccessor.builder(H.class).delegate(ClassLoader.class, new String[] {"defineClass", "findLoadedClass"}).build();
		} finally {
			AsmShared.local().setUnbuffered(false);
		}
	}

	public static Class<?> findLoadedClass(ClassLoader loader, String name) { return def.findLoadedClass(loader, name); }
	public static Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd) { return def.defineClass(loader, name, b, off, len, pd); }

	public ClassDefiner(ClassLoader parent) { super(parent); }

	public final Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError { return defineClass(name, IOUtil.SharedCoder.get().wrap(bytes)); }
	public final Class<?> defineClass(IClass data) { return defineClass(null, Parser.toByteArrayShared(data)); }
	public Class<?> defineClass(String name, ByteList buf) throws ClassFormatError {
		if (ClassDumper.DUMP_ENABLED) ClassDumper.dump("define", buf);

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