package roj.asm.insn;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roj234
 * @since 2025/3/20 14:43
 */
public final class SwitchCase implements Comparable<SwitchCase> {
	public final int value;
	public Label target;

	public SwitchCase(int value, Label target) {
		this.value = value;
		this.target = target;
	}

	public int bci() {return target.getValue();}

	@Override
	public int compareTo(@NotNull SwitchCase o) {return Integer.compare(value, o.value);}

	@Override
	public String toString() {return value + " => " + target;}
}
