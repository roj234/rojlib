package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.tree.attr.AttrCode;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.collect.BSLowHeap;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/29 1:55
 */
public final class SwitchInsnNode extends InsnNode {
	public SwitchInsnNode(byte code) {
		super(code);
		this.targets = new BSLowHeap<>(null);
	}

	public SwitchInsnNode(byte code, InsnNode def, List<SwitchEntry> targets) {
		super(code);
		this.def = def;
		this.targets = targets;
		targets.sort(null);
	}

	@Override
	protected boolean validate() {
		switch (code) {
			case Opcodes.TABLESWITCH:
			case Opcodes.LOOKUPSWITCH: return true;
		}
		return false;
	}

	@Override
	public int nodeType() {
		return T_SWITCH;
	}

	public InsnNode def;
	public List<SwitchEntry> targets;

	private Label defLabel;

	public void branch(int number, InsnNode label) {
		targets.add(new SwitchEntry(number, label));
	}

	@Override
	public void preSerialize(Map<InsnNode, Label> labels) {
		for (int i = 0; i < targets.size(); i++) {
			SwitchEntry entry = targets.get(i);
			InsnNode node = validate((InsnNode) entry.pos);
			entry.pos = node;
			entry.insnPos = AttrCode.monitorNode(labels, node);
		}

		defLabel = AttrCode.monitorNode(labels, def = validate(def));
	}
	public int pad(int codeLength) {
		return 3 - (codeLength & 3);
	}

	@Override
	public int nodeSize(int prevBci) {
		return pad(prevBci) + (code == Opcodes.TABLESWITCH ? 1 + 4 + 8 + (targets.size() << 2) : 1 + 8 + (targets.size() << 3));
	}

	@Override
	public void serialize(CodeWriter cw) {
		SwitchSegment sin = CodeWriter.newSwitch(code);
		sin.def = defLabel;
		sin.targets = targets;
		cw.switches(sin);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString()).append(" {\n");
		for (SwitchEntry entry : targets) {
			sb.append("        ").append(entry.key).append(" => #").append(entry.getBci()).append('\n');
		}
		return sb.append("        default: ").append(def).append("\n    }").toString();
	}

}