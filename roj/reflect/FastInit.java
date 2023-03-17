package roj.reflect;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.CodeWriter;
import roj.util.ByteList;

import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * @author Roj234
 * @since 2020/12/6 15:04
 */
public final class FastInit {
	private static Object CallbackBuffer;

	public static void syncCallback(Object handle) {
		CallbackBuffer = handle;
	}

	public static Object make(IClass var) {
		return make(var, ClassDefiner.INSTANCE);
	}
	public static Object make(IClass var, ClassDefiner l) {
		ByteList buf = Parser.toByteArrayShared(var);
		String name = var.name().replace('/', '.');

		synchronized (FastInit.class) {
			try {
				Class<?> klass = l.defineClassC(name, buf);
				FieldAccessor.u.ensureClassInitialized(klass);
				if (null == CallbackBuffer) throw new IllegalStateException("初始化失败: 你是否调用了prepare()来写入<clinit>?");
				return CallbackBuffer;
			} catch (Throwable e) {
				throw new IllegalStateException("初始化失败", e);
			} finally {
				CallbackBuffer = null;
			}
		}
	}

	public static void prepare(ConstantData clz) {
		clz.npConstructor();

		CodeWriter cw = clz.newMethod(PUBLIC|AccessFlag.STATIC, "<clinit>", "()V");
		cw.visitSize(2,0);

		cw.newObject(clz.name);
		cw.invoke(Opcodes.INVOKESTATIC, FastInit.class.getName().replace('.', '/'), "syncCallback", "(Ljava/lang/Object;)V");
		cw.one(Opcodes.RETURN);
		cw.finish();
	}
}
