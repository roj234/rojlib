package roj.asmx.event;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.cp.CstClass;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.insn.AttrCode;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.InsnList;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.util.Context;
import roj.asmx.ITransformer;
import roj.asmx.NodeFilter;
import roj.asmx.NodeTransformer;
import roj.asmx.TransformException;
import roj.collect.MyHashSet;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/21 0021 13:20
 */
public final class EventTransformer implements ITransformer, NodeTransformer<ClassNode> {
	private final MyHashSet<String> knownEvents = new MyHashSet<>("roj/asmx/event/Event");

	public static EventTransformer register(NodeFilter fd) {
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
		MethodNode clinit = data.getMethodObj("<clinit>");
		AbstractCodeWriter c;
		if (clinit == null) {
			CodeWriter c1 = data.newMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", "()V");
			c1.visitSize(1, 0);
			c = c1;
		} else {
			c = new InsnList();
		}

		c.ldc(new CstClass(data.name()));
		c.invoke(Opcodes.INVOKESTATIC, "roj/asmx/event/EventBus", "registerEvent", "(Ljava/lang/Class;)V");

		if (clinit != null) {
			AttrCode code = clinit.getAttribute(data.cp, Attribute.Code);
			if (code.stackSize == 0) code.stackSize = 1;
			code.instructions.replaceRange(0,0, (InsnList) c, false);
		}
	}

	@Override
	public boolean transform(ClassNode data, ClassNode ctx) throws TransformException {
		int fid = data.newField(Opcodes.ACC_PRIVATE, "cancelled", "Z");

		CodeWriter c = data.newMethod(Opcodes.ACC_PUBLIC, "cancel", "()V");
		c.visitSize(2, 1);
		c.one(Opcodes.ALOAD_0);
		c.ldc(1);
		c.field(Opcodes.PUTFIELD, data, fid);
		c.one(Opcodes.RETURN);

		c = data.newMethod(Opcodes.ACC_PUBLIC, "isCanceled", "()Z");
		c.visitSize(1, 1);
		c.one(Opcodes.ALOAD_0);
		c.field(Opcodes.GETFIELD, data, fid);
		c.one(Opcodes.IRETURN);

		c = data.newMethod(Opcodes.ACC_PUBLIC, "isCancellable", "()Z");
		c.visitSize(1, 1);
		c.ldc(1);
		c.one(Opcodes.IRETURN);
		return true;
	}
}