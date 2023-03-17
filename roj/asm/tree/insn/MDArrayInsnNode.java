package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.CstClass;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class MDArrayInsnNode extends InsnNode implements IIndexInsnNode {
	public MDArrayInsnNode() {
		super(Opcodes.MULTIANEWARRAY);
	}

	public MDArrayInsnNode(CstClass clazz, int dimension) {
		super(Opcodes.MULTIANEWARRAY);
		this.owner = clazz.name().str();
		setDimension(dimension);
	}

	public MDArrayInsnNode(String clazz, int dimension) {
		super(Opcodes.MULTIANEWARRAY);
		this.owner = clazz;
		setDimension(dimension);
	}

	@Override
	public int nodeType() {
		return T_MULTIANEWARRAY;
	}

	public String owner;
	private byte dimension;

	public int getIndex() {
		return dimension&0xFF;
	}

	@Override
	public void setIndex(int index) {
		throw new UnsupportedOperationException("Cannot change dimension by setter, manually cast plz");
	}

	public void setDimension(int dimension) {
		if (dimension < 0 || dimension > 255)
			throw new ArrayIndexOutOfBoundsException(dimension);
		this.dimension = (byte) dimension;
	}

	@Override
	protected boolean validate() {
		return code == Opcodes.MULTIANEWARRAY;
	}

	@Override
	public void serialize(CodeWriter cw) {
		cw.multiArray(owner, dimension);
	}

	@Override
	public int nodeSize(int prevBci) {
		return 4;
	}

	public String toString() {
		return OpcodeUtil.toString0(code) + " " + TypeHelper.parseField(owner);
	}
}