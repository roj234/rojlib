package roj.asm.frame.node;

import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Segment;
import roj.lavac.asm.Variable;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.ALOAD;
import static roj.asm.Opcodes.ILOAD;
import static roj.asm.frame.VarType.*;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
public final class LazyLoadStore extends Segment {
	private final Variable v;
	private byte code;

	public LazyLoadStore(Variable v, boolean store) {
		this.v = v;
		switch (v.curType.type) {
			case TOP: throw new IllegalStateException();
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
				code = (byte) (ILOAD - 1 + v.curType.type);
				break;
			default:
				code = ALOAD;
		}
		if (store) code += 33;
	}

	@Override
	protected boolean put(CodeWriter to) {
		DynByteBuf ob = to.bw;
		int begin = ob.wIndex();

		to.vars(code, v.slot);

		begin = ob.wIndex() - begin;
		assert length() == begin;
		return false;
	}

	@Override
	protected int length() {
		int slot = v.slot;
		if (slot <= 3) return 1; // CODE
		else if (slot < 255) return 2; // CODE V1
		else return 4; // WIDE CODE V1 V2
	}
}
