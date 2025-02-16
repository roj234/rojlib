package roj.asm.frame;

import org.jetbrains.annotations.NotNull;
import roj.asm.insn.Label;
import roj.io.IOUtil;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Frame {
	public static final int same = 0;
	public static final int same_local_1_stack = 64;
	public static final int same_local_1_stack_ex = 247;
	public static final int chop = 250, chop2 = 249, chop3 = 248;
	public static final int same_ex = 251;
	public static final int append = 252;
	public static final int full = 255;

	public static int toExactFrameType(int b) {
		final int b1 = b & 0xFF;
		if ((b1 & 128) == 0) return (b1 & 64) == 0 ? same : same_local_1_stack;
		return switch (b1) {
			case 247 -> same_local_1_stack_ex;
			// chop 1-3
			case 248, 249, 250 -> b1;
			case 251 -> same_ex;
			case 252, 253, 254 -> append;
			case 255 -> full;
			default -> throw new IllegalArgumentException("unknown frame "+b1);
		};
	}

	public static String getName(int type) {
		if ((type & 128) == 0) return (type & 64) == 0 ? "same" : "same_local_1_stack";
		return switch (type) {
			case 247 -> "same_local_1_stack_ex";
			case 248, 249, 250 -> "chop " + (251 - type);
			case 251 -> "same_ex";
			case 252, 253, 254 -> "append " + (type - 251);
			case 255 -> "full";
			default -> "unknown";
		};
	}

	public static final Var2[] NONE = new Var2[0];

	public short type;
	public int bci;
	public Label monitor_bci;
	@NotNull
	public Var2[] locals = NONE, stacks = NONE;

	public static Frame fromVarietyType(int type) {
		return new Frame(toExactFrameType(type));
	}

	public Frame() { this.type = -1; }
	public Frame(int type) { this.type = (short) type; }

	public int bci() {
		return monitor_bci == null ? bci : monitor_bci.getValue();
	}

	public String toString() { return toString(IOUtil.getSharedCharBuf(), 0).toString(); }
	public CharList toString(CharList sb, int prefix) {
		sb.padEnd(' ', prefix).append(getName(type)).append(" #").append(bci());
		if (locals.length > 0) {
			sb.append('\n').padEnd(' ', prefix).append("Local: [");
			int i = 0;
			while (true) {
				sb.append(locals[i++]);
				if (i == locals.length) break;
				sb.append(", ");
			}
			sb.append(']');
		}
		if (stacks.length > 0) {
			sb.append('\n').padEnd(' ', prefix).append("Stack: [");
			int i = 0;
			while (true) {
				sb.append(stacks[i++]);
				if (i == stacks.length) break;
				sb.append(", ");
			}
			sb.append(']');
		}
		return sb.append('\n');

	}

	public void locals(Var2... arr) {
		locals = arr;
	}
	public void stacks(Var2... arr) {
		stacks = arr;
	}
}