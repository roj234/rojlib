package roj.collect;

import roj.asm.tree.ConstantData;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.reflect.ClassDefiner;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/4/5 0005 19:22
 */
final class ArrayHasher {
	private static final CharMap<Hasher<?>> built = new CharMap<>();
	@SuppressWarnings("unchecked")
	static <T> Hasher<T> array(Class<T> type) {
		if (!type.getComponentType().isPrimitive()) type = (Class<T>) Object[].class;

		Type clz = TypeHelper.class2type(type);

		Hasher<?> h = built.get((char) clz.type);
		block:
		if (h == null) {
			synchronized (built) {
				if ((h = built.get((char) clz.type)) != null) break block;
			}

			ConstantData hasher = new ConstantData();
			hasher.name("roj/collect/Hasher$A" + (char) clz.type);
			hasher.addInterface("roj/collect/Hasher");

			CodeWriter cw = hasher.newMethod(ACC_PUBLIC | ACC_FINAL, "hashCode", "(Ljava/lang/Object;)I");
			cw.visitSize(1, 2);
			cw.one(ALOAD_1);
			cw.clazz(CHECKCAST, clz);
			cw.invokeS("java/util/Arrays", "hashCode", "("+clz.toDesc()+")I");
			cw.one(IRETURN);

			cw = hasher.newMethod(ACC_PUBLIC | ACC_FINAL, "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
			cw.visitSize(2, 3);
			cw.one(ALOAD_1);
			cw.clazz(CHECKCAST, clz);
			cw.one(ALOAD_2);
			cw.clazz(CHECKCAST, clz);
			cw.invokeS("java/util/Arrays", "equals", "("+clz.toDesc()+clz.toDesc()+")Z");
			cw.one(IRETURN);

			ClassDefiner.premake(hasher);
			h = (Hasher<?>) ClassDefiner.make(hasher);
			built.put((char) clz.type, h);
		}
		return (Hasher<T>) h;
	}
}