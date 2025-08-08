package roj.reflect;

import roj.asm.AsmCache;
import roj.asm.ClassDefinition;
import roj.asm.ClassNode;
import roj.ci.annotation.Public;
import roj.util.ByteList;

import java.security.ProtectionDomain;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2021/6/16 1:31
 */
public final class ClassDefiner extends ClassLoader {
	// 这里必须inline/weak(), 否则循环引用
	private static final H def = Bypass.builder(H.class).inline().delegate(ClassLoader.class, new String[] {"defineClass", "findLoadedClass"}).build();
	@Public
	private interface H {
		Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd);
		Class<?> findLoadedClass(ClassLoader loader, String name);
	}
	@Deprecated // since 2025/07/11
	public static Class<?> findLoadedClass(ClassLoader loader, String name) { return def.findLoadedClass(loader, name); }
	// Security Critical, PangerSM Hooked
	public static Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd) { return def.defineClass(loader, name, b, off, len, pd); }

	public static final ClassLoader APP_LOADER = ClassDefiner.class.getClassLoader();
	//static {registerAsParallelCapable();}
	private final String id;
	public ClassDefiner(ClassLoader parent, String id) {super(parent);this.id = id;}
	public String toString() {return "ClassDefiner<"+id+">";}

	public static Object newInstance(ClassNode node) {return newInstance(node, APP_LOADER);}
	public static Object newInstance(ClassNode node, ClassLoader loader) {
		try {
			ByteList buf = AsmCache.toByteArrayShared(node);
			Class<?> klass = loader == null ? Reflection.defineWeakClass(buf) : defineClass(loader, buf);
			return U.allocateInstance(klass);
		} catch (Throwable e) {
			throw new IllegalStateException("类构造失败", e);
		}
	}

	public static Class<?> defineGlobalClass(ClassDefinition data) {return defineClass(APP_LOADER, data);}
	public static Class<?> defineClass(ClassLoader loader, ClassDefinition data) {return defineClass(loader, AsmCache.toByteArrayShared(data));}
	private static Class<?> defineClass(ClassLoader loader, ByteList data) throws ClassFormatError {
		if (loader == null) throw new NullPointerException("classLoader cannot be null");
		if (Debug.CLASS_DUMP) Debug.dump("define", data);

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) data = sm.preDefineClass(data);

		int off = data.arrayOffset()+data.rIndex;
		return def.defineClass(loader, null, data.list, off, data.readableBytes(), loader.getClass().getProtectionDomain());
	}
}