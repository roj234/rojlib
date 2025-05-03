package roj.asm.insn;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roj234
 * @since 2025/3/20 14:43
 */
public final class SwitchTarget implements Comparable<SwitchTarget> {
	public final int value;
	public Label target;

	public SwitchTarget(int value, Label target) {
		this.value = value;
		this.target = target;
	}

	public int bci() {return target.getValue();}

	@Override
	public int compareTo(@NotNull SwitchTarget o) {return Integer.compare(value, o.value);}

	@Override
	public String toString() {return value + " => " + target;}
}
