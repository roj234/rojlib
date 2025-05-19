package roj.asm.insn;

import roj.asm.Opcodes;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2022/11/17 12:53
 */
public class JumpTo extends Segment {
	public byte code;
	public Label target;

	public int fv_bci;

	public JumpTo(byte code, Label target) {
		this.code = code;
		this.target = target;
		assert target != null;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public boolean put(CodeWriter to, int segmentId) {
		if (!target.isValid()) throw new IllegalStateException("target label is not valid: "+target);

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
			default:
				if (((short) off) != off) throw new IllegalStateException("offset too large");
				o.put(code).putShort(off);
			break;
		}

		return len != newLen;
	}

	@Override public int length() { return Opcodes.showOpcode(code).endsWith("_W")?5:3; }
	@Override public final boolean isTerminate() { return code == GOTO || code == GOTO_W; }
	@Override public final boolean willJumpTo(int block, int offset) { return (offset == -1 || target.offset == offset) && target.getBlock() == block; }

	@Override public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		Label rx = copyLabel(target, to, blockMoved, clone);
		return clone?new JumpTo(code,rx):this;
	}

	@Override public final String toString() { return Opcodes.showOpcode(code)+"("+target+")"; }
}