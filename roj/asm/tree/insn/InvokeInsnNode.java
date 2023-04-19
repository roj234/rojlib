package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.CstRef;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;

/**
 * invokevirtual invokespecial invokestatic invokeinterface
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class InvokeInsnNode extends InsnNode {
	public InvokeInsnNode(byte code, String owner, String name, String desc) {
		super(code);
		this.owner = owner;
		this.name = name;
		this.desc = desc;
	}

	public InvokeInsnNode(byte code, CstRef ref) {
		super(code);
		this.owner = ref.getClassName();
		this.name = ref.desc().getName().getString();
		this.desc = ref.desc().getType().getString();
	}

	protected boolean validate() { return OpcodeUtil.category(code) == OpcodeUtil.CATE_METHOD; }
	public final int nodeType() { return T_INVOKE; }

	public String owner, name, desc;

	@Deprecated
	public final String awslDesc() {
		return desc;
	}

	public void serialize(CodeWriter cw) {
		if (Opcodes.INVOKEINTERFACE == code) cw.invokeItf(owner, name, desc);
		else cw.invoke(code, owner, name, desc);
	}

	public int nodeSize(int prevBci) { return 3; }

	public final void fullDesc(String desc) {
		int cIdx = desc.indexOf('.');

		this.owner = desc.substring(0, cIdx);

		int nIdx = desc.indexOf(':', cIdx + 1);
		String name = desc.substring(cIdx + 1, nIdx);
		if (name.charAt(0) == '"') {
			name = name.substring(1, name.length() - 1);
		}
		this.name = name;

		this.desc = desc.substring(nIdx + 1);
	}

	public final String toString() {
		String s = TypeHelper.humanize(TypeHelper.parseMethod(desc),owner.substring(owner.lastIndexOf('/')+1)+'.'+name,true);
		return super.toString() + ' ' + s;
	}
}