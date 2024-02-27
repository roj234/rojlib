package roj.reflect;

import roj.ReferenceByGeneratedClass;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.visitor.CodeWriter;
import roj.util.ByteList;

import java.security.ProtectionDomain;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/6/16 1:31
 */
public final class ClassDefiner extends ClassLoader {
	static {registerAsParallelCapable();}
	private final String desc;
	public ClassDefiner(ClassLoader parent, String desc) {super(parent);this.desc = desc;}
	public String toString() {return "ClassDefiner<"+desc+">";}

	public static final ClassLoader APP_LOADER = ClassDefiner.class.getClassLoader();

	private static final ThreadLocal<Object> Callback = new ThreadLocal<>();
	@ReferenceByGeneratedClass
	public static void __(Object handle) { Callback.set(handle); }

	public static void premake(ConstantData clz) {
		clz.npConstructor();

		CodeWriter cw = clz.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		cw.visitSize(2,0);

		cw.newObject(clz.name);
		cw.invoke(INVOKESTATIC, ClassDefiner.class.getName().replace('.', '/'), "__", "(Ljava/lang/Object;)V");
		cw.one(RETURN);
		cw.finish();
	}
	public static Object make(ConstantData data) {return make(data, APP_LOADER);}
	public static Object make(ConstantData data, ClassLoader cl) {
		try {
			ByteList buf = Parser.toByteArrayShared(data);
			Class<?> klass = cl == null ? ReflectionUtils.defineWeakClass(buf) : defineClass(cl, null, buf);
			return postMake(klass);
		} catch (Throwable e) {
			throw new IllegalStateException("初始化失败", e);
		}
	}
	public static Object postMake(Class<?> klass) {
		try {
			ReflectionUtils.ensureClassInitialized(klass);

			Object o = Callback.get();
			if (null == o) throw new IllegalStateException("未在过去调用premake()");
			return o;
		} finally {
			Callback.remove();
		}
	}

	// 用weak(), 这样不会走ClassDefiner#defineClass分支
	private static final H def = Bypass.builder(H.class).inline().delegate(ClassLoader.class, new String[] {"defineClass", "findLoadedClass"}).build();
	private interface H {
		Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd);
		Class<?> findLoadedClass(ClassLoader loader, String name);
	}

	public static Class<?> findLoadedClass(ClassLoader loader, String name) { return def.findLoadedClass(loader, name); }
	// DPS Hooked, also used by DPS Hooks
	public static Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd) { return def.defineClass(loader, name, b, off, len, pd); }

	public static Class<?> defineGlobalClass(IClass data) {return defineClass(APP_LOADER, data);}
	public static Class<?> defineClass(ClassLoader cl, IClass data) {return defineClass(cl, null, Parser.toByteArrayShared(data));}
	public static Class<?> defineClass(ClassLoader cl, String name, ByteList buf) throws ClassFormatError {
		if (cl == null) throw new NullPointerException("classLoader cannot be null");
		if (Debug.CLASS_DUMP) Debug.dump("define", buf);

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) buf = sm.checkDefineClass(name, buf);

		int off = buf.arrayOffset()+buf.rIndex;
		try {
			return def.defineClass(cl, name, buf.list, off, buf.readableBytes(), cl.getClass().getProtectionDomain());
		} finally {
			buf.rIndex = buf.wIndex();
		}
	}
}