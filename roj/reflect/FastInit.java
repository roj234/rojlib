package roj.reflect;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.visitor.CodeWriter;
import roj.util.ByteList;

import static roj.asm.Opcodes.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2020/12/6 15:04
 */
public final class FastInit {
	private static final ThreadLocal<Object> Callback = new ThreadLocal<>();
	public static void __(Object handle) { Callback.set(handle); }

	public static Object make(ConstantData var) { return make(var, ClassDefiner.INSTANCE); }
	public static Object make(ConstantData var, ClassDefiner l) {
		ByteList buf = Parser.toByteArrayShared(var);
		String name = var.name().replace('/', '.');

		try {
			Class<?> klass = l.defineClass(name, buf);
			u.ensureClassInitialized(klass);
			//MethodHandles.lookup().ensureInitialized(klass);

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

		CodeWriter cw = clz.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		cw.visitSize(2,0);

		cw.newObject(clz.name);
		cw.invoke(INVOKESTATIC, FastInit.class.getName().replace('.', '/'), "__", "(Ljava/lang/Object;)V");
		cw.one(RETURN);
		cw.finish();
	}
}