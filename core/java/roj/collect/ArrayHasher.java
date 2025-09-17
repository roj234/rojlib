package roj.collect;

import roj.asm.ClassNode;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.optimizer.FastVarHandle;
import roj.reflect.Reflection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/4/5 19:22
 */
@FastVarHandle
final class ArrayHasher {
	private static final Hasher<?>[] INSTANCES = new Hasher<?>[26];
	private static final VarHandle INSTANCES$L$ARRAY = MethodHandles.arrayElementVarHandle(Hasher[].class);
	@SuppressWarnings("unchecked")
	static <T> Hasher<T> array(Class<T> type) {
		if (!type.getComponentType().isPrimitive()) type = (Class<T>) Object[].class;

		Type clz = Type.getType(type);

		int OFFSET = clz.type - Type.BYTE;
		Hasher<?> h = INSTANCES[OFFSET];
		block:
		if (h == null) {
			synchronized (INSTANCES) {
				if ((h = (Hasher<?>) INSTANCES$L$ARRAY.getVolatile(INSTANCES, OFFSET)) != null) break block;
			}

			ClassNode hasher = new ClassNode();
			hasher.name("roj/collect/Hasher$A"+(char) clz.type);
			hasher.addInterface("roj/collect/Hasher");

			CodeWriter cw = hasher.newMethod(ACC_PUBLIC | ACC_FINAL, "hashCode", "(Ljava/lang/Object;)I");
			cw.visitSize(1, 2);
			cw.insn(ALOAD_1);
			cw.clazz(CHECKCAST, clz);
			cw.invokeS("java/util/Arrays", "hashCode", "("+clz.toDesc()+")I");
			cw.insn(IRETURN);

			cw = hasher.newMethod(ACC_PUBLIC | ACC_FINAL, "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
			cw.visitSize(2, 3);
			cw.insn(ALOAD_1);
			cw.clazz(CHECKCAST, clz);
			cw.insn(ALOAD_2);
			cw.clazz(CHECKCAST, clz);
			cw.invokeS("java/util/Arrays", "equals", "("+clz.toDesc()+clz.toDesc()+")Z");
			cw.insn(IRETURN);

			h = (Hasher<?>) Reflection.createInstance(ArrayHasher.class.getClassLoader(), hasher);
			INSTANCES$L$ARRAY.setVolatile(INSTANCES, OFFSET, h);
		}
		return (Hasher<T>) h;
	}
}