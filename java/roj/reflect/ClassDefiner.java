package roj.reflect;

import roj.ReferenceByGeneratedClass;
import roj.asm.ClassNode;
import roj.asm.IClass;
import roj.asm.Parser;
import roj.asm.insn.CodeWriter;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.Helpers;

import java.security.ProtectionDomain;

import static roj.asm.Opcodes.*;
import static roj.reflect.Unaligned.U;

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
	private static final boolean __UseAllocateInstance = true;

	private static final ThreadLocal<Object> Callback = new ThreadLocal<>();
	@ReferenceByGeneratedClass
	public static void __(Object handle) { Callback.set(handle); }

	public static void premake(ClassNode clz) {
		clz.npConstructor();
		if (__UseAllocateInstance) return;

		CodeWriter cw = clz.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		cw.visitSize(2,0);

		cw.newObject(clz.name());
		cw.invoke(INVOKESTATIC, ClassDefiner.class.getName().replace('.', '/'), "__", "(Ljava/lang/Object;)V");
		cw.insn(RETURN);
		cw.finish();
	}
	public static Object make(ClassNode data) {return make(data, APP_LOADER);}
	public static Object make(ClassNode data, ClassLoader cl) {
		try {
			ByteList buf = Parser.toByteArrayShared(data);
			Class<?> klass = cl == null ? ReflectionUtils.defineWeakClass(buf) : defineClass(cl, null, buf);
			return postMake(klass);
		} catch (Throwable e) {
			throw new IllegalStateException("初始化失败", e);
		}
	}
	public static Object postMake(Class<?> klass) {
		if (__UseAllocateInstance) {
			try {
				return U.allocateInstance(klass);
			} catch (InstantiationException e) {
				Logger.FALLBACK.fatal("创建对象失败", e);
				return Helpers.maybeNull();
			}
		}

		ReflectionUtils.ensureClassInitialized(klass);

		var o = Callback.get();
		if (null == o) throw new IllegalStateException("未在过去调用premake()");
		Callback.remove();
		return o;
	}

	// 用weak(), 这样不会走ClassDefiner#defineClass分支
	// 另外，这里不允许deferred
	private static final H def = Bypass.builder(H.class).inline().delegate(ClassLoader.class, new String[] {"defineClass", "findLoadedClass"}).build();
	@Java22Workaround
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