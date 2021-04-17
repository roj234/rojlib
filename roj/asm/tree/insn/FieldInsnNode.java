package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.cst.CstRefField;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;

/**
 * getstatic putstatic getfield putfield
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class FieldInsnNode extends InsnNode {
	public FieldInsnNode(byte code, String owner, String name, Type type) {
		super(code);

		this.owner = owner;
		this.name = name;
		this.rawType = TypeHelper.getField(type);
	}

	public FieldInsnNode(byte code, String owner, String name, String type) {
		super(code);

		if (!Type.isValid(type.charAt(0))) throw new IllegalArgumentException("别把class name当type!");

		this.owner = owner;
		this.name = name;
		this.rawType = type;
	}

	// net/xxx/abc.DEF:LXXXX javap
	public FieldInsnNode(byte code, String desc) {
		super(code);
		int cIdx = desc.indexOf('.');

		this.owner = desc.substring(0, cIdx);

		int nIdx = desc.indexOf(':', cIdx + 1);
		String name = desc.substring(cIdx + 1, nIdx);
		if (name.charAt(0) == '"') {
			name = name.substring(1, name.length() - 1);
		}
		this.name = name;

		this.rawType = desc.substring(nIdx + 1);
	}

	public FieldInsnNode(byte code, CstRefField ref) {
		super(code);
		this.owner = ref.className();
		this.name = ref.descName();
		this.rawType = ref.descType();
	}

	public String owner, name, rawType;

	protected boolean validate() { return OpcodeUtil.category(code) == OpcodeUtil.CATE_FIELD; }
	public int nodeType() { return T_FIELD; }

	public void serialize(CodeWriter cw) { cw.field(code, owner, name, rawType); }

	@Override
	public int nodeSize(int prevBci) { return 3; }

	public String toString() {
		return OpcodeUtil.toString0(code) + " " + owner.substring(owner.lastIndexOf('/') + 1) + "." + name + " : " + TypeHelper.parseField(rawType);
	}
}