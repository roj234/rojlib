package roj.reflect;

import roj.asm.tree.ConstantData;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.CodeWriter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/5/17 0017 9:57
 */
public class Proxy {
	public static int proxyClass(ConstantData data, Class<?>[] itfs, BiFunction<Method, CodeWriter, Boolean> overrider, int... extraFields) {
		if (!itfs[0].isInterface()) throw new IllegalArgumentException("not interface");

		String itfStr = itfs[0].getName().replace('.', '/');
		data.addInterface(itfStr);
		data.addInterface("java/util/function/Function");

		int fid = data.newField(0, "$proxy", new Type(itfStr));
		data.npConstructor();

		CodeWriter c = data.newMethod(AccessFlag.PUBLIC | AccessFlag.FINAL, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
		c.visitSize(3, 2);

		c.one(ALOAD_1);
		c.clazz(CHECKCAST, "[Ljava/lang/Object;");
		c.one(ASTORE_1);

		c.clazz(NEW, data.name());
		c.one(DUP);
		c.invokeD(data.name, "<init>", "()V");
		c.one(ASTORE_0);

		c.one(ALOAD_0);
		c.unpackArray(1, 0, data.fields.get(fid).fieldType());
		c.field(PUTFIELD, data, fid);

		for (int i = 0; i < extraFields.length; i++) {
			int fid_ = extraFields[i];
			c.one(ALOAD_0);
			c.unpackArray(1, i+1, data.fields.get(fid_).fieldType());
			c.field(PUTFIELD, data, fid_);
		}

		c.one(ALOAD_0);
		c.one(ARETURN);

		proxyMethods(data, itfs, fid, overrider);
		return fid;
	}

	public static void proxyMethods(ConstantData data, Class<?>[] itfs, int fid, BiFunction<Method, CodeWriter, Boolean> overrider) {
		String itfStr = data.fields.get(fid).fieldType().owner;

		for (Class<?> itf : itfs) {
			for (Method m : itf.getMethods()) {
				String desc = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
				if (data.getMethod(m.getName(), desc) >= 0) continue;

				CodeWriter c = data.newMethod(AccessFlag.PUBLIC | AccessFlag.FINAL, m.getName(), desc);
				int s = TypeHelper.paramSize(desc)+1;
				c.visitSize(s,s);
				c.one(ALOAD_0);
				c.field(GETFIELD, data, fid);
				List<Type> par = c.mn.parameters();
				for (int i = 0; i < par.size(); i++) {
					c.varLoad(par.get(i), i+1);
				}

				if (!overrider.apply(m, c)) {
					c.invokeItf(itfStr, c.mn.name(), desc);
				}
				c.return_(c.mn.returnType());
				c.finish();
			}
		}
	}
}
