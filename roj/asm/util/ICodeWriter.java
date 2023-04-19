package roj.asm.util;

import roj.asm.OpcodeUtil;
import roj.asm.cst.*;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/4/19 0019 19:52
 */
public interface ICodeWriter {
	static void assertCate(byte code, int i) {
		if (i != (i = OpcodeUtil.category(code))) throw new IllegalArgumentException("参数错误,不支持的操作码类型/"+i+"/"+OpcodeUtil.toString0(code));
	}
	static void assertTrait(byte code, int i) {
		if ((i & OpcodeUtil.trait(code)) == 0) throw new IllegalArgumentException("参数错误,不支持的操作码特性/"+OpcodeUtil.trait(code)+"/"+OpcodeUtil.toString0(code));
	}

	void newArray(byte type);
	void multiArray(String clz, int dimension);
	void clazz(byte code, String clz);
	void increase(int id, int count);
	void ldc(Constant c);
	default void ldc(String c) { ldc(new CstString(c)); }
	void ldc(int value);
	default void ldc(long n) {
		if (n != 0 && n != 1) ldc(new CstLong(n));
		else one((byte) (LCONST_0 + (int)n));
	}
	default void ldc(float n) {
		if (n != 0 && n != 1 && n != 2) ldc(new CstFloat(n));
		else one((byte) (FCONST_0 + (int)n));
	}
	default void ldc(double n) {
		if (n != 0 && n != 1) ldc(new CstDouble(n));
		else one((byte) (DCONST_0 + (int)n));
	}
	/**
	 * The third and fourth operand bytes of each invokedynamic instruction must have the value zero. <br>
	 * Thus, we ignore it again(Previous in InvokeItfInsnNode).
	 */
	void invokeDyn(int idx, String name, String desc, int type);
	void invokeItf(String owner, String name, String desc);
	void invoke(byte code, String owner, String name, String desc);
	default void invoke(byte code, MethodNode m) { invoke(code, m.ownerClass(), m.name(), m.rawDesc()); }
	default void invoke(byte code, IClass cz, int id) {
		MoFNode node = cz.methods().get(id);
		invoke(code, cz.name(), node.name(), node.rawDesc());
	}
	default void invokeV(String owner, String name, String desc) { invoke(INVOKEVIRTUAL, owner, name, desc); }
	default void invokeS(String owner, String name, String desc) { invoke(INVOKESTATIC, owner, name, desc); }
	default void invokeD(String owner, String name, String desc) { invoke(INVOKESPECIAL, owner, name, desc); }
	void field(byte code, String owner, String name, String type);
	// no FieldNode <= no ownerClass()
	default void field(byte code, IClass cz, int id) {
		MoFNode node = cz.fields().get(id);
		field(code, cz.name(), node.name(), node.rawDesc());
	}
	default void field(byte code, String desc) {
		int cIdx = desc.indexOf('.');
		String owner = desc.substring(0, cIdx++);

		int nIdx = desc.indexOf(':', cIdx);
		String name = desc.substring(cIdx, nIdx);
		if (name.charAt(0) == '"') name = name.substring(1, name.length()-1);

		field(code, owner, name, desc.substring(nIdx+1));
	}
	default void field(byte code, String owner, String name, Type type) {
		field(code, owner, name, TypeHelper.getField(type));
	}
	void one(byte code);
	// todo protected, writer should use ldc(int)
	void smallNum(byte code, int value);
	void var(byte code, int value);
	void jsr(int value);
	void ret(int value);
}
