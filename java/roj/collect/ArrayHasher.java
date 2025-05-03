package roj.collect;

import roj.asm.ClassNode;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.reflect.ClassDefiner;
import roj.reflect.Unaligned;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/4/5 19:22
 */
final class ArrayHasher {
	private static final Hasher<?>[] INSTANCES = new Hasher<?>[26];
	@SuppressWarnings("unchecked")
	static <T> Hasher<T> array(Class<T> type) {
		if (!type.getComponentType().isPrimitive()) type = (Class<T>) Object[].class;

		Type clz = Type.fromJavaType(type);

		int OFFSET = clz.type - Type.BYTE;
		Hasher<?> h = INSTANCES[OFFSET];
		block:
		if (h == null) {
			synchronized (INSTANCES) {
				if ((h = INSTANCES[OFFSET]) != null) break block;
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

			ClassDefiner.premake(hasher);
			h = (Hasher<?>) ClassDefiner.make(hasher);
			Unaligned.U.putObjectVolatile(INSTANCES, Unaligned.ARRAY_OBJECT_BASE_OFFSET + (long) OFFSET * Unaligned.ARRAY_OBJECT_INDEX_SCALE, h);
		}
		return (Hasher<T>) h;
	}
}