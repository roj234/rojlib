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
	Constant constant;

	Ldc(byte code, Constant constant) {
		this.code = code;
		this.constant = constant;
	}

	@Override
	public boolean write(CodeWriter to, int segmentId) {
		int prevCode = code;

		DynByteBuf o = to.bw;
		int index = to.cpw.fit(constant);
		if (index <= 255) {
			o.put(code = LDC).put(index);
		} else {
			o.put(code = LDC_W).putShort(index);
		}

		return code != prevCode;
	}

	@Override public int length() { return code-0x10; } // LDC=0x12, LDC_W=0x13
	@Override public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) { return clone?new Ldc(code, constant.clone()):this; }
	@Override public String toString() { return Opcodes.toString(code)+"("+ constant.toString()+")"; }

	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Ldc segment = (Ldc) o;

		return constant.equals(segment.constant);
	}
	@Override public int hashCode() { return constant.hashCode(); }
}