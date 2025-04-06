package roj.asm.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.insn.Label;
import roj.io.IOUtil;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Frame {
	public static final byte
			same = 0, same_local_1_stack = 64, same_local_1_stack_ex = -9,
			chop = -6, chop2 = -7, chop3 = -8, same_ex = -5, append = -4, full = -1;

	public static final Var2[] NONE = new Var2[0];

	byte type;
	public int bci;
	@Nullable public Label monitoredBci;
	@NotNull public Var2[] locals = NONE, stacks = NONE;

	public static Frame fromByte(int type) {
		if ((type & 128) == 0) {
			type = (type & 64) == 0 ? same : same_local_1_stack;
		} else {
			type = switch ((byte) type) {
				case same_local_1_stack_ex, full, chop, chop2, chop3, same_ex -> type;
				case append, -3, -2 -> append;
				default -> throw new IllegalArgumentException("unknown frame type "+type);
			};
		}
		return new Frame(type);
	}

	public Frame() {this.type = full;}
	public Frame(int type) {this.type = (byte) type;}

	public byte type() {return type;}
	public int bci() {return monitoredBci == null ? bci : monitoredBci.getValue();}

	public String toString() {return toString(IOUtil.getSharedCharBuf(), 0).toString();}
	public CharList toString(CharList sb, int prefix) {
		String typeStr;
		if ((type & 128) == 0) {
			typeStr = (type & 64) == 0 ? "same" : "same_local_1_stack";
		} else {
			typeStr = switch (type) {
				case same_ex -> "same";
				case same_local_1_stack_ex -> "same_local_1_stack";
				case chop, chop2, chop3 -> "chop" + (type + 5);
				case append -> "append";
				case full -> "full";
				default -> "illegal #"+type;
			};
		}
		sb.padEnd(' ', prefix).append(typeStr).append('@').append(bci());
		if (typeStr.equals("append")) display(sb.append(": "), locals);
		else if (typeStr.equals("same_local_1_stack")) sb.append(": ").append(stacks[0]);
		else if (typeStr.equals("full")) {
			if (locals.length > 0) {
				sb.append('\n').padEnd(' ', prefix+4).append("Local: ");
				display(sb, locals);
			}
			if (stacks.length > 0) {
				sb.append('\n').padEnd(' ', prefix+4).append("Stack: ");
				display(sb, stacks);
			}
		}
		return sb;
	}
	private static void display(CharList sb, @NotNull Var2[] locals) {
		sb.append('[');
		int i = 0;
		while (true) {
			sb.append(locals[i++]);
			if (i == locals.length) break;
			sb.append(", ");
		}
		sb.append(']');
	}

	public void locals(Var2... arr) {
		if (type != full && type != append || arr.length > 3)
			throw new IllegalStateException(this+" cannot set "+arr.length+" locals");
		locals = arr;
	}
	public void stacks(Var2... arr) {
		if (type != full && (type != same_local_1_stack && type != same_local_1_stack_ex) || arr.length != 1) {
			throw new IllegalStateException(this+" cannot set "+arr.length+" stacks");
		}
		stacks = arr;
	}
}