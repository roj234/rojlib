package roj.asm.visitor;

import roj.asm.frame.Var2;
import roj.io.IOUtil;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Frame2 {
	public static final int same = 0;
	public static final int same_local_1_stack = 64;
	public static final int same_local_1_stack_ex = 247;
	public static final int chop = 250, chop2 = 249, chop3 = 248;
	public static final int same_ex = 251;
	public static final int append = 252;
	public static final int full = 255;

	public static int toExactFrameType(int b) {
		final int b1 = b & 0xFF;
		if ((b1 & 128) == 0) {
			// 64 - 127
			return (b1 & 64) == 0 ? same : same_local_1_stack;
			// 0 - 63
		}
		switch (b1) {
			case 247: return same_local_1_stack_ex;
			// chop 1-3
			case 248: case 249: case 250: return b1;
			case 251: return same_ex;
			case 252: case 253: case 254: return append;
			case 255: return full;
		}
		throw new IllegalArgumentException("Undefined frame type" + b1);
	}

	public static String getName(int type) {
		if ((type & 128) == 0) {
			// 64 - 127
			return (type & 64) == 0 ? "same" : "same_local_1_stack";
			// 0 - 63
		}
		switch (type) {
			case 247: return "same_local_1_stack_ex";
			case 248: case 249: case 250: return "chop "+(251-type);
			case 251: return "same_ex";
			case 252: case 253: case 254: return "append "+(type-251);
			case 255: return "full";
		}
		return "unknown";
	}

	public short type;
	public int target;
	public Label target3;
	public Var2[] locals, stacks;

	public static Frame2 fromVarietyType(int type) {
		return new Frame2(toExactFrameType(type));
	}

	public Frame2() { this.type = -1; }
	public Frame2(int type) { this.type = (short) type; }

	public int bci() {
		return target3 == null ? target : target3.getValue();
	}

	public String toString() { return toString(IOUtil.getSharedCharBuf(), 0).toString(); }
	public CharList toString(CharList sb, int prefix) {
		sb.padEnd(' ', prefix).append(getName(type)).append(" #").append(bci());
		if (locals != null && locals.length > 0) {
			sb.append('\n').padEnd(' ', prefix).append("Local: [");
			int i = 0;
			while (true) {
				sb.append(locals[i++]);
				if (i == locals.length) break;
				sb.append(", ");
			}
			sb.append(']');
		}
		if (stacks != null && stacks.length > 0) {
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