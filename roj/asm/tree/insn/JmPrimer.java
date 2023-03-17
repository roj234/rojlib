package roj.asm.tree.insn;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.OpcodeUtil;
import roj.asm.visitor.CodeWriter;

import java.util.List;

@Internal
public final class JmPrimer extends InsnNode {
	public JmPrimer(byte code, int def) {
		super(code);
		this.def = def;
		this.switcher = null;
	}

	public JmPrimer(byte code, int def, List<SwitchEntry> switcher) {
		super(code);
		this.def = def;
		this.switcher = switcher;
	}

	@Override
	public int nodeType() { return 123; }

	public int selfIndex, arrayIndex;
	public int def;
	public final List<SwitchEntry> switcher;

	@Override
	public String toString() {
		return "Jump(" + OpcodeUtil.toString0(code) + ") => " + def + " / " + switcher;
	}

	@Override
	public void serialize(CodeWriter cw) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int nodeSize(int prevBci) {
		return 0;
	}

	public InsnNode bake(InsnNode target) {
		return new JumpInsnNode(code, target);
	}
}