package roj.asmx.event;

import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.util.Context;
import roj.asm.visitor.AbstractCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.XAttrCode;
import roj.asm.visitor.XInsnList;
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
public final class EventTransformer implements ITransformer, NodeTransformer<ConstantData> {
	private final MyHashSet<String> knownEvents = new MyHashSet<>("roj/asmx/event/Event");

	public static EventTransformer register(NodeFilter fd) {
		EventTransformer tr = new EventTransformer();
		fd.annotatedClass("roj/asmx/event/Cancellable", tr);
		return tr;
	}

	@Override
	public boolean transform(String name, Context ctx) throws TransformException {
		ConstantData data = ctx.getData();
		String parent = data.parent();
		synchronized (knownEvents) {
			if (!knownEvents.contains(parent)) return false;
			if ((data.modifier()&Opcodes.ACC_FINAL) == 0) knownEvents.add(parent);
		}

		Signature signature = data.parsedAttr(data.cp, Attribute.SIGNATURE);
		if (signature != null) {
			Map<String, List<IType>> typeParams = signature.typeParams;
			//if (typeParams.size() > 1) throw new TransformException("事件类"+data.name+"不能有超过一个泛型参数！");
			if (typeParams.size() > 0 &&
				null == data.getMethodObj("getGenericType", "()Ljava/lang/String;") &&
				null == data.getMethodObj("getGenericValueType", "()Ljava/lang/Class;")) {
				throw new TransformException("具有泛型参数的事件类"+data.name+"必须实现getGenericType或getGenericValueType方法");
			}

			//return true;
		}

		return false;
	}

	private static void callRegister(ConstantData data) {
		MethodNode clinit = data.getMethodObj("<clinit>");
		AbstractCodeWriter c;
		if (clinit == null) {
			CodeWriter c1 = data.newMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", "()V");
			c1.visitSize(1, 0);
			c = c1;
		} else {
			c = new XInsnList();
		}

		c.ldc(new CstClass(data.name));
		c.invoke(Opcodes.INVOKESTATIC, "roj/asmx/event/EventBus", "registerEvent", "(Ljava/lang/Class;)V");

		if (clinit != null) {
			XAttrCode code = clinit.parsedAttr(data.cp, Attribute.Code);
			if (code.stackSize == 0) code.stackSize = 1;
			code.instructions.replaceRange(0,0, (XInsnList) c, false);
		}
	}

	@Override
	public boolean transform(ConstantData data, ConstantData ctx) throws TransformException {
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