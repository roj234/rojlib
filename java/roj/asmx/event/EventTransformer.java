package roj.asmx.event;

import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asmx.*;
import roj.collect.HashSet;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/21 13:20
 */
public final class EventTransformer implements Transformer, ConstantPoolHooks.Hook<ClassNode> {
	private final HashSet<String> knownEvents = new HashSet<>("roj/asmx/event/Event");

	public static EventTransformer register(ConstantPoolHooks fd) {
		EventTransformer tr = new EventTransformer();
		fd.annotatedClass("roj/asmx/event/Cancellable", tr);
		return tr;
	}

	@Override
	public boolean transform(String name, Context ctx) throws TransformException {
		ClassNode data = ctx.getData();
		String parent = data.parent();
		if (!knownEvents.contains(parent)) return false;

		if ((data.modifier()&Opcodes.ACC_FINAL) == 0) {
			synchronized (knownEvents) {knownEvents.add(name);}
		}

		Signature signature = data.getAttribute(data.cp, Attribute.SIGNATURE);
		if (signature != null) {
			Map<String, List<IType>> typeParams = signature.typeParams;
			//if (typeParams.size() > 1) throw new TransformException("事件类"+data.name+"不能有超过一个泛型参数！");
			if (typeParams.size() > 0 &&
				null == data.getMethodObj("getGenericType", "()Ljava/lang/String;") &&
				null == data.getMethodObj("getGenericValueType", "()Ljava/lang/Class;")) {
				throw new TransformException("具有泛型参数的事件类"+data.name()+"必须实现getGenericType或getGenericValueType方法");
			}

			//return true;
		}

		return false;
	}

	private static void callRegister(ClassNode data) {
		TransformUtil.prependStaticConstructor(data, c -> {
			c.ldc(new CstClass(data.name()));
			c.invoke(Opcodes.INVOKESTATIC, "roj/asmx/event/EventBus", "registerEvent", "(Ljava/lang/Class;)V");
		});
	}

	@Override
	public boolean transform(ClassNode context, ClassNode node) throws TransformException {
		int fid = context.newField(Opcodes.ACC_PRIVATE, "cancelled", "Z");

		CodeWriter c = context.newMethod(Opcodes.ACC_PUBLIC, "cancel", "()V");
		c.visitSize(2, 1);
		c.insn(Opcodes.ALOAD_0);
		c.ldc(1);
		c.field(Opcodes.PUTFIELD, context, fid);
		c.insn(Opcodes.RETURN);

		c = context.newMethod(Opcodes.ACC_PUBLIC, "isCanceled", "()Z");
		c.visitSize(1, 1);
		c.insn(Opcodes.ALOAD_0);
		c.field(Opcodes.GETFIELD, context, fid);
		c.insn(Opcodes.IRETURN);

		c = context.newMethod(Opcodes.ACC_PUBLIC, "isCancellable", "()Z");
		c.visitSize(1, 1);
		c.ldc(1);
		c.insn(Opcodes.IRETURN);
		return true;
	}
}