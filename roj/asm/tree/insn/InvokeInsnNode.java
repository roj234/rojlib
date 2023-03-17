package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstRef;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnList;
import roj.asm.visitor.CodeWriter;

import java.util.List;

/**
 * invokevirtual invokespecial invokestatic
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class InvokeInsnNode extends IInvokeInsnNode implements IClassInsnNode {
	public InvokeInsnNode(byte code) {
		super(code);
	}

	public InvokeInsnNode(byte code, String fullDesc) {
		super(code);
		fullDesc(fullDesc);
	}

	public InvokeInsnNode(byte code, String owner, String name, String desc) {
		super(code);
		this.owner = owner;
		this.name = name;
		this.rawDesc = desc;
	}

	public InvokeInsnNode(byte code, CstRef ref) {
		super(code);
		this.owner = ref.getClassName();
		this.name = ref.desc().getName().getString();
		this.rawDesc = ref.desc().getType().getString();
	}

	public InvokeInsnNode(byte code, IClass clazz, int index) {
		super(code);
		MoFNode mn = clazz.methods().get(index);
		this.owner = clazz.name();
		this.name = mn.name();
		this.rawDesc = mn.rawDesc();
	}

	public InvokeInsnNode(IClass clazz, int index) {
		MoFNode mn = clazz.methods().get(index);
		setOpcode((mn.accessFlag() & AccessFlag.STATIC) != 0 ? Opcodes.INVOKESTATIC :
			(mn.accessFlag() & (AccessFlag.PRIVATE | AccessFlag.FINAL)) != 0 ? Opcodes.INVOKESPECIAL :
			Opcodes.INVOKEVIRTUAL);
		this.owner = clazz.name();
		this.name = mn.name();
		this.rawDesc = mn.rawDesc();
	}

	@Override
	protected boolean validate() {
		switch (code) {
			case Opcodes.INVOKESTATIC:
			case Opcodes.INVOKESPECIAL:
			case Opcodes.INVOKEVIRTUAL:
				return true;
		}
		return false;
	}

	@Override
	/**
	 * fn validFor invokespecial() bool
	 *   return name == <init> ||
	 *      name in owner.methods ||
	 *      name in owner.superClass.methods ||
	 *      name in owner.interfaces.each(methods) ||
	 *      name in Object.class
	 */ public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
		if (name.startsWith("<")) {
			if (!name.equals("<init>")) throw new IllegalArgumentException("Calling methods name begin with '<' ('\\u003c') can only be named '<init>'");
			if (code != Opcodes.INVOKESPECIAL) throw new IllegalArgumentException("Only the invokespecial instruction is allowed to invoke an instance initialization method");
		}
	}

	public String owner;

	@Override
	public final int nodeType() {
		return T_INVOKE;
	}

	@Override
	public final String owner() {
		return owner;
	}

	@Override
	public final void owner(String clazz) {
		// noinspection all
		this.owner = clazz.toString();
	}

	public void serialize(CodeWriter cw) {
		cw.invoke(code, owner, name, rawDesc());
	}

	@Override
	public int nodeSize(int prevBci) {
		return 3;
	}

	@Override
	public final void fullDesc(String desc) {
		int cIdx = desc.indexOf('.');

		this.owner = desc.substring(0, cIdx);

		int nIdx = desc.indexOf(':', cIdx + 1);
		String name = desc.substring(cIdx + 1, nIdx);
		if (name.charAt(0) == '"') {
			name = name.substring(1, name.length() - 1);
		}
		this.name = name;

		this.rawDesc = desc.substring(nIdx + 1);
		if (params != null) {
			params.clear();
			TypeHelper.parseMethod(rawDesc, params);
			returnType = params.remove(params.size() - 1);
		}
	}

	@Override
	public String fullDesc() {
		return owner + '.' + name + ':' + rawDesc;
	}

	public final String toString() {
		StringBuilder sb = new StringBuilder(super.toString()).append(' ').append(returnType()).append(' ').append(owner.substring(owner.lastIndexOf('/') + 1)).append('.').append(name).append('(');

		List<Type> params = parameters();
		if (!params.isEmpty()) {
			int i = 0;
			while (true) {
				Type par = params.get(i++);
				sb.append(par);
				if (i == params.size()) break;
				sb.append(", ");
			}
		}
		return sb.append(')').toString();
	}
}