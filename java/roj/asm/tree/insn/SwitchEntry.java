package roj.asm.tree.insn;

import org.jetbrains.annotations.NotNull;
import roj.asm.visitor.Label;

/**
 * @author Roj233
 * @since 2022/2/26 19:58
 */
public final class SwitchEntry implements Comparable<SwitchEntry> {
	public final int val;
	public Label pos;

	public SwitchEntry(int val, Label pos) {
		this.val = val;
		this.pos = pos;
	}

	@Override
	public int compareTo(@NotNull SwitchEntry o) { return Integer.compare(val, o.val); }
	public int getBci() { return pos.getValue(); }

	@Override
	public String toString() { return val+" => "+pos; }
}