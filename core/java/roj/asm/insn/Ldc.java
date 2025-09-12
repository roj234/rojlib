package roj.asm.insn;

import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.LDC;
import static roj.asm.Opcodes.LDC_W;

/**
 * @author Roj234
 * @since 2023/10/1 23:07
 */
final class Ldc extends Segment {
	private byte code;
	final Constant c;

	Ldc(byte code, Constant c) {
		this.code = code;
		this.c = c;
	}

	@Override
	public boolean write(CodeWriter to, int segmentId) {
		int prevCode = code;

		DynByteBuf o = to.bw;
		int index = to.cpw.fit(c);
		if (index <= 255) {
			o.put(code = LDC).put(index);
		} else {
			o.put(code = LDC_W).putShort(index);
		}

		return code != prevCode;
	}

	@Override public int length() { return code-0x10; } // LDC=0x12, LDC_W=0x13
	@Override public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) { return clone?new Ldc(code,c.clone()):this; }
	@Override public String toString() { return Opcodes.toString(code)+"("+c.toString()+")"; }

	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Ldc segment = (Ldc) o;

		return c.equals(segment.c);
	}
	@Override public int hashCode() { return c.hashCode(); }
}