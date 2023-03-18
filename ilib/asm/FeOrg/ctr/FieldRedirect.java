package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.MoFNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InsnList;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.Context;

import java.util.List;

public class FieldRedirect implements ContextClassTransformer {
	private final String clsName, fieldType, methodDesc;

	private final String bypass;

	public FieldRedirect(String desc, String cls, String type, String bypass) {
		this.desc = desc;
		this.clsName = cls;
		this.fieldType = type;
		this.methodDesc = "()" + type;
		this.bypass = bypass;
	}

	String desc;

	@Override
	public String toString() {
		return getClass().getName() + '[' + desc + ']';
	}

	@Override
	public void transform(String trName, Context ctx) {
		if (!clsName.equals(trName)) return;

		ConstantData data = ctx.getData();

		String fieldRef = null;

		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			MoFNode f = fields.get(i);
			if (fieldType.equals(f.rawDesc()) && fieldRef == null) {
				fieldRef = f.name();
				continue;
			}
			if (fieldType.equals(f.rawDesc())) throw new RuntimeException("Error processing " + clsName + " - found a duplicate holder field");
		}
		if (fieldRef == null) throw new RuntimeException("Error processing " + clsName + " - no holder field declared (is the code somehow obfuscated?)");

		MethodNode getMethod = null;

		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode m = methods.get(i);
			if (m.name().equals(bypass)) continue;
			if (methodDesc.equals(m.rawDesc()) && getMethod == null) {
				getMethod = m;
				continue;
			}
			if (methodDesc.equals(m.rawDesc())) throw new RuntimeException("Error processing " + clsName + " - duplicate get method found");
		}
		if (getMethod == null) throw new RuntimeException("Error processing " + clsName + " - no get method found (is the code somehow obfuscated?)");

		for (int j = 0; j < methods.size(); j++) {
			MethodNode mn = methods.get(j);
			if (mn.name().equals(bypass)) continue;

			AttrCode code = mn.parsedAttr(data.cp, Attribute.Code);
			if (code == null) continue;

			InsnList insn = code.instructions;
			for (int i = 0; i < insn.size(); i++) {
				InsnNode node = insn.get(i);
				if (node.getOpcode() == Opcodes.GETFIELD) {
					FieldInsnNode fi = (FieldInsnNode) node;
					// GETFIELD
					if (fieldRef.equals(fi.name)) {
						insn.set(i, new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, data.name, getMethod.name(), getMethod.rawDesc()));
					}
				}
			}
		}
	}
}
