package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.cst.CstRefField;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;

import static roj.asm.Opcodes.*;

/**
 * getstatic putstatic getfield putfield
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class FieldInsnNode extends InsnNode implements IClassInsnNode {
	public FieldInsnNode(byte code) {
		super(code);
	}

	public FieldInsnNode(byte code, String owner, String name, Type type) {
		super(code);

		this.owner = owner;
		this.name = name;
		this.type = type;
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
		int cIdx = desc.indexOf(".");

		this.owner = desc.substring(0, cIdx);

		int nIdx = desc.indexOf(":", cIdx + 1);
		String name = desc.substring(cIdx + 1, nIdx);
		if (name.charAt(0) == '"') {
			name = name.substring(1, name.length() - 1);
		}
		this.name = name;

		this.rawType = desc.substring(nIdx + 1);
	}

	public FieldInsnNode(byte code, CstRefField ref) {
		super(code);
		this.owner = ref.getClassName();
		this.name = ref.desc().getName().getString();
		this.rawType = ref.desc().getType().getString();
	}

	public String owner, name, rawType;
	private Type type;

	public FieldInsnNode(byte code, IClass clazz, int index) {
		super(code);
		MoFNode field = clazz.fields().get(index);
		this.owner = clazz.name();
		this.name = field.name();
		this.type = TypeHelper.parseField(field.rawDesc());
	}

	public Type getType() {
		if (type == null) {
			try {
				type = TypeHelper.parseField(rawType);
			} catch (Exception e) {
				e.printStackTrace();
				type = Type.std(Type.VOID);
			}
		}
		return type;
	}

	public void setType(Type type) {
		rawType = null;
		this.type = type;
	}

	@Override
	public int nodeType() {
		return T_FIELD;
	}

	@Override
	public void owner(String clazz) {
		// noinspection all
		this.owner = clazz.toString();
	}

	public String owner() {
		return owner;
	}

	public void serialize(CodeWriter cw) {
		if (type != null) rawType = TypeHelper.getField(type);
		cw.field(code, owner, name, rawType);
	}

	@Override
	public int nodeSize(int prevBci) {
		return 3;
	}

	@Override
	protected boolean validate() {
		switch (code) {
			case GETFIELD:
			case GETSTATIC:
			case PUTFIELD:
			case PUTSTATIC:
				return true;
		}
		return false;
	}

	public String toString() {
		return OpcodeUtil.toString0(code) + " " + owner.substring(owner.lastIndexOf('/') + 1) + "." + name + " : " + getType();
	}
}