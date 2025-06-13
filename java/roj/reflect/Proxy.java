package roj.reflect;

import roj.asm.ClassNode;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;

import static roj.asm.Opcodes.*;

/**
 * 你觉得这是AOP那这就是AOP，我觉得{@link roj.asmx.injector.CodeWeaver}也是AOP
 * @author Roj234
 * @since 2023/5/17 9:57
 */
public class Proxy {
	/**
	 * 在类节点上创建代理实现结构
	 *
	 * @param node 目标类节点（将被修改）
	 * @param types 要实现的类数组，第一项要么是接口，要么具有至少protected的无参构造器，其余项必须是接口
	 * @param overrider 方法重写器，返回true表示已处理该方法
	 * @param extraFieldIds 额外字段的字段ID，这些字段需要通过apply传递参数
	 *
	 * <h3>参数数组约定：</h3>
	 * <pre>{@code
	 * Object[] params = {
	 *   proxyTarget,    // 对应$proxy字段
	 *   extraField1,    // 对应第一个额外字段
	 *   extraField2     // 对应第二个额外字段
	 * };}</pre>
	 */
	public static void proxyClass(ClassNode node, Class<?>[] types, BiFunction<Method, CodeWriter, Boolean> overrider, int... extraFieldIds) {
		for (int i = 1; i < types.length; i++) {
			if (!types[i].isInterface()) throw new IllegalArgumentException("not interface");
		}

		String instanceType = types[0].getName().replace('.', '/');
		if (types[0].isInterface()) {
			node.addInterface(instanceType);
		} else {
			node.parent(instanceType);
		}

		node.npConstructor();
		node.addInterface("java/util/function/Function");

		int delegation = node.newField(0, "$proxy", Type.klass(instanceType));

		// apply (newInstance)
		var c = node.newMethod(ACC_PUBLIC | ACC_FINAL, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
		c.visitSize(3, 2);

		c.insn(ALOAD_1);
		c.clazz(CHECKCAST, "[Ljava/lang/Object;");
		c.insn(ASTORE_1);

		c.clazz(NEW, node.name());
		c.insn(DUP);
		c.invokeD(node.name(), "<init>", "()V");
		c.insn(ASTORE_0);

		c.insn(ALOAD_0);
		c.unpackArray(1, 0, node.fields.get(delegation).fieldType());
		c.field(PUTFIELD, node, delegation);

		for (int i = 0; i < extraFieldIds.length; i++) {
			int id = extraFieldIds[i];
			c.insn(ALOAD_0);
			c.unpackArray(1, i+1, node.fields.get(id).fieldType());
			c.field(PUTFIELD, node, id);
		}

		c.insn(ALOAD_0);
		c.insn(ARETURN);
		//end apply

		for (Class<?> itf : types) {
			for (Method method : itf.getMethods()) {
				String desc = TypeHelper.class2asm(method.getParameterTypes(), method.getReturnType());
				if (node.getMethod(method.getName(), desc) >= 0) continue;

				var cw = node.newMethod(ACC_PUBLIC | ACC_FINAL, method.getName(), desc);
				int slots = TypeHelper.paramSize(desc)+1;
				cw.visitSize(slots,slots);
				cw.insn(ALOAD_0);
				cw.field(GETFIELD, node, delegation);

				List<Type> parameters = cw.mn.parameters();
				for (int i = 0; i < parameters.size(); i++) {
					cw.varLoad(parameters.get(i), i+1);
				}

				if (!overrider.apply(method, cw)) {
					cw.invokeItf(instanceType, cw.mn.name(), desc);
				}
				cw.return_(cw.mn.returnType());
				cw.finish();
			}
		}
	}
}