package roj.asm.visitor;

import roj.asm.frame.Var2;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.InsnNode;
import roj.collect.IntMap;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Frame2 {
	public static final int same = 0;
	public static final int same_local_1_stack = 64;
	public static final int same_local_1_stack_ex = 247;
	public static final int chop = 248;
	public static final int chop2 = 249;
	public static final int chop3 = 250;
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
			case 247:
				return "same_local_1_stack_ex";
			case 248:
			case 249:
			case 250:
				return "chop " + (type - 247);
			case 251:
				return "same_ex";
			case 252:
			case 253:
			case 254:
				return "append " + (type - 251);
			case 255:
				return "full";
		}
		return "unknown";
	}

	public short type;
	public int target;
	public InsnNode target2;
	public Var2[] locals, stacks;

	public final void addMonitorT(Map<InsnNode, Label> labels) {
		if (locals != null) _TLoop(locals, labels);
		if (stacks != null) _TLoop(stacks, labels);
	}
	private static void _TLoop(Var2[] stacks, Map<InsnNode, Label> labels) {
		for (Var2 v : stacks) {
			if (v.bci2 != null) {
				v.bci2 = InsnNode.validate(v.bci2);
				AttrCode.monitorNode(labels, v.bci2);
			}
		}
	}

	public final void addMonitorV(IntMap<Label> w) {
		if (locals != null) {
			for (Var2 v : locals) {
				int bci = v.bci();
				if (bci >= 0 && !w.containsKey(bci)) w.putInt(bci, new Label());
			}
		}
		if (stacks != null) {
			for (Var2 v : stacks) {
				int bci = v.bci();
				if (bci >= 0 && !w.containsKey(bci)) w.putInt(bci, new Label());
			}
		}
	}
	public boolean applyMonitorV(IntMap<Label> w) {
		boolean b = false;
		if (locals != null) {
			for (Var2 v : locals)
				if (v.bci() >= 0 && v.bci != (v.bci = w.get(v.bci).getValue())) b = true;
		}
		if (stacks != null) {
			for (Var2 v : stacks)
				if (v.bci() >= 0 && v.bci != (v.bci = w.get(v.bci).getValue())) b = true;
		}
		return b;
	}

	public static Frame2 fromVarietyType(int type) {
		return new Frame2(toExactFrameType(type));
	}

	public Frame2() {
		this.type = -1;
	}

	public Frame2(int type) {
		this.type = (short) type;
	}

	public Frame2(int type, InsnNode node) {
		this.type = (short) type;
		this.target2 = node;
	}

	public Frame2(int type, int node) {
		this.type = (short) type;
		this.target = node;
	}

	public int bci() {
		return target2 == null ? target : InsnNode.validate(target2).bci;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("  ").append(getName(type)).append(" #").append(bci());
		if (locals != null && locals.length > 0) {
			sb.append("\n      Local: [");
			int i = 0;
			while (true) {
				sb.append(locals[i++]);
				if (i == locals.length) break;
				sb.append(", ");
			}
			sb.append(']');
		}
		if (stacks != null && stacks.length > 0) {
			sb.append("\n      Stack: [");
			int i = 0;
			while (true) {
				sb.append(stacks[i++]);
				if (i == stacks.length) break;
				sb.append(", ");
			}
			sb.append(']');
		}
		return sb.append('\n').toString();
	}

	public void locals(Var2... arr) {
		locals = arr;
	}
	public void stacks(Var2... arr) {
		stacks = arr;
	}
}
