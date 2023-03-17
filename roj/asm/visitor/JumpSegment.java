package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.util.DynByteBuf;

import java.util.Locale;

import static roj.asm.Opcodes.GOTO;
import static roj.asm.Opcodes.GOTO_W;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
final class JumpSegment extends Segment {
	final Label target;
	int bci;
	byte code;

	JumpSegment(byte code, Label target) {
		this.code = code;
		this.target = target;
		this.length = code == GOTO_W ? 5 : 3;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public boolean put(CodeWriter to) {
		DynByteBuf o = to.bw;
		int off = target.getValue() - to.bci;

		bci = to.bci;
		switch (code) {
			case GOTO:
			case GOTO_W:
				int newLen;
				if (((short) off) != off) {
					code = GOTO_W;
					o.put(code).putInt(off);
					newLen = 5;
				} else {
					code = GOTO;
					o.put(code).putShort(off);
					newLen = 3;
				}

				if (length != newLen) {
					length = newLen;
					return true;
				}

				break;
			default:
				o.put(code).putShort(off);
				break;
		}
		return false;
	}

	@Override
	public String toString() {
		return OpcodeUtil.toString0(code).toLowerCase(Locale.ROOT) + target;
	}
}
