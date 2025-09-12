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

	protected int bci;
	// 仅供FrameVisitor使用
	public int bci() {return bci;}

	public JumpTo(byte code, Label target) {
		this.code = code;
		this.target = target;
		assert target != null;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public boolean write(CodeWriter to, int segmentId) {
		if (!target.isValid()) throw new IllegalStateException("target is not valid: "+target);

		DynByteBuf o = to.bw;

		bci = to.bci;
		int off = target.getValue() - bci;

		int len = 3;
		int newLen = length();

		switch (code) {
			case JSR, JSR_W -> {
				if (((short) off) != off) {
					o.put(code = JSR_W).putInt(off);
					len = 5;
				} else {
					o.put(code = JSR).putShort(off);
				}
			}
			case GOTO, GOTO_W -> {
				if (((short) off) != off) {
					o.put(code = GOTO_W).putInt(off);
					len = 5;
				} else {
					o.put(code = GOTO).putShort(off);
				}
			}
			default -> {
				if (((short) off) != off) throw new IllegalStateException("offset too large");
				o.put(code).putShort(off);
			}
		}

		return len != newLen;
	}

	@Override public int length() { return code>=GOTO_W?5:3; }
	@Override public final boolean isTerminate() { return code >= GOTO; } // GOTO, GOTO_W, JSR, JSR_W
	@Override public final boolean willJumpTo(int block, int offset) { return (offset == -1 || target.offset == offset) && target.getBlock() == block; }

	@Override public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		Label rx = copyLabel(target, to, blockMoved, clone);
		return clone?new JumpTo(code,rx):this;
	}

	@Override public final String toString() { return Opcodes.toString(code)+"("+target+")"; }
}