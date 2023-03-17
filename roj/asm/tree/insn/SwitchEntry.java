package roj.asm.tree.insn;

import roj.asm.visitor.Label;

import javax.annotation.Nonnull;

/**
 * @author Roj233
 * @since 2022/2/26 19:58
 */
public final class SwitchEntry implements Comparable<SwitchEntry> {
	public final int key;
	public Object pos;
	public Label insnPos;

	public SwitchEntry(int key, int pos) {
		this.key = key;
		this.pos = pos;
	}

	public SwitchEntry(int key, Number pos) {
		this.key = key;
		this.pos = pos;
	}

	public SwitchEntry(int key, InsnNode pos) {
		this.key = key;
		this.pos = pos;
	}

	@Override
	public int compareTo(@Nonnull SwitchEntry o) {
		return Integer.compare(key, o.key);
	}

	public int getBci() {
		return pos instanceof InsnNode ? insnPos ==null ? ((InsnNode) pos).bci : insnPos.getValue() : ((Number) pos).intValue();
	}
}
