package roj.asm.tree.insn;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.OpcodeUtil;
import roj.asm.visitor.CodeWriter;

import java.util.List;

import static roj.asm.Opcodes.*;

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
	protected boolean validate() {
		switch (code) {
			case TABLESWITCH:
			case LOOKUPSWITCH:
			case IFEQ:
			case IFNE:
			case IFLT:
			case IFGE:
			case IFGT:
			case IFLE:
			case IF_icmpeq:
			case IF_icmpne:
			case IF_icmplt:
			case IF_icmpge:
			case IF_icmpgt:
			case IF_icmple:
			case IF_acmpeq:
			case IF_acmpne:
			case IFNULL:
			case IFNONNULL:
			case GOTO:
			case GOTO_W:
				return true;
		}
		return false;
	}

	@Override
	public int nodeType() {
		return 123;
	}

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