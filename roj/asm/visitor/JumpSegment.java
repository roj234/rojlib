package roj.asm.visitor;

import roj.asm.Opcodes;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class JumpSegment extends Segment {
	public byte code;
	public Label target;

	public int fv_bci;

	JumpSegment(byte code, Label target) {
		this.code = code;
		this.target = target;
		assert target != null;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public boolean put(CodeWriter to) {
		DynByteBuf o = to.bw;
		int off = target.getValue() - to.bci;

		fv_bci = to.bci;

		int len = 3;
		int newLen = length();

		switch (code) {
			case JSR: case JSR_W:
				if (((short) off) != off) {
					o.put(code = JSR_W).putInt(off);
					len = 5;
				} else {
					o.put(code = JSR).putShort(off);
				}
			break;
			case GOTO: case GOTO_W:
				if (((short) off) != off) {
					o.put(code = GOTO_W).putInt(off);
					len = 5;
				} else {
					o.put(code = GOTO).putShort(off);
				}
			break;
			default: o.put(code).putShort(off); break;
		}

		return len != newLen;
	}

	@Override
	public int length() { return Opcodes.showOpcode(code).endsWith("_W")?5:3; }

	@Override
	Segment move(AbstractCodeWriter list, int blockMoved, int mode) {
		Label rx = copyLabel(target, list, blockMoved, mode);
		return mode==XInsnList.REP_CLONE?new JumpSegment(code,rx):this;
	}

	@Override
	public String toString() { return Opcodes.showOpcode(code)+"(b"+target.getBlock()+" + "+target.getOffset()+")"; }
}