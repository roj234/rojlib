package roj.reflect;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.CodeWriter;
import roj.util.ByteList;

import static roj.asm.util.AccessFlag.PUBLIC;
import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2020/12/6 15:04
 */
public final class FastInit {
	private static final ThreadLocal<Object> Callback = new ThreadLocal<>();
	public static void __(Object handle) { Callback.set(handle); }

	public static Object make(ConstantData var) { return make(var, ClassDefiner.INSTANCE, true); }
	public static Object make(ConstantData var, ClassDefiner l, boolean autoUnload) {
		ByteList buf = Parser.toByteArrayShared(var);
		String name = var.name().replace('/', '.');

		try {
			Class<?> klass = null;
			try {
				if (autoUnload && ReflectionUtils.JAVA_VERSION < 17) {
					byte[] b;

					ILSecurityManager sm = ILSecurityManager.getSecurityManager();
					if (sm != null) b = sm.checkDefineAnonymousClass(buf);
					else b = buf.toByteArray();

					klass = u.defineAnonymousClass(FastInit.class, b, null);
				}
			} catch (Throwable ignored) {}
			if (klass == null) klass = l.defineClass(name, buf);

			u.ensureClassInitialized(klass);

			Object o = Callback.get();
			if (null == o) throw new IllegalStateException("初始化失败: 你是否调用了prepare()来写入<clinit>?");
			return o;
		} catch (Throwable e) {
			throw new IllegalStateException("初始化失败", e);
		} finally {
			Callback.remove();
		}
	}

	public static void prepare(ConstantData clz) {
		clz.npConstructor();

		CodeWriter cw = clz.newMethod(PUBLIC|AccessFlag.STATIC, "<clinit>", "()V");
		cw.visitSize(2,0);

		cw.newObject(clz.name);
		cw.invoke(Opcodes.INVOKESTATIC, FastInit.class.getName().replace('.', '/'), "__", "(Ljava/lang/Object;)V");
		cw.one(Opcodes.RETURN);
		cw.finish();
	}
}