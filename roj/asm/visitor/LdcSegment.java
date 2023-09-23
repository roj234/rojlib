package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.asm.cst.Constant;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.LDC;
import static roj.asm.Opcodes.LDC_W;

/**
 * @author Roj234
 * @since 2023/10/1 0001 23:07
 */
final class LdcSegment extends Segment {
	byte code;
	Constant c;

	LdcSegment(byte code, Constant c) {
		this.code = code;
		this.c = c;
	}

	@Override
	public boolean put(CodeWriter to) {
		int prevCode = code;

		DynByteBuf o = to.bw;
		int index = to.cpw.reset(c).getIndex();
		if (index <= 255) {
			o.put(code = LDC).put(index);
		} else {
			o.put(code = LDC_W).putShort(index);
		}

		return code != prevCode;
	}

	@Override
	protected int length() { return code-0x10; } // LDC=0x12, LDC_W=0x13
	@Override
	Segment move(AbstractCodeWriter list, int blockMoved, int mode) { return mode==XInsnList.REP_CLONE ?new LdcSegment(code,c.clone()):this; }
	@Override
	public String toString() { return OpcodeUtil.toString0(code)+"("+c.getEasyReadValue()+")"; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		LdcSegment segment = (LdcSegment) o;

		return c.equals(segment.c);
	}

	@Override
	public int hashCode() { return c.hashCode(); }
}
