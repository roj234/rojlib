package roj.asm.type;

import roj.asm.tree.insn.InsnNode;

/**
 * @author Roj234
 * @since 2020/8/10 17:53
 */
public class LocalVariable {
	public LocalVariable(String name, IType type) {
		this.name = name;
		this.type = type;
	}

	public LocalVariable(int slot, String name, IType type, InsnNode start, InsnNode end) {
		this.slot = slot;
		this.name = name;
		this.type = type;
		this.start = start;
		this.end = end;
	}

	public String name;
	public IType type;
	public InsnNode start, end;
	public int slot;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LocalVariable that = (LocalVariable) o;
		return slot == that.slot && start == that.start && end == that.end;
	}

	@Override
	public int hashCode() {
		return slot * 31 + start.hashCode();
	}

	public String toString() {
		return String.valueOf(slot) + '\t' + type + '\t' + name + '\t' + start + '\t' + end;
	}
}
