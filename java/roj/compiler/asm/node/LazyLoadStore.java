package roj.compiler.asm.node;

import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Segment;
import roj.compiler.asm.Variable;
import roj.compiler.resolve.TypeCast;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
public final class LazyLoadStore extends Segment {
	private final Variable v;
	private final boolean store;

	public LazyLoadStore(Variable v, boolean store) {
		this.v = v;
		this.store = store;
	}

	@Override
	protected boolean put(CodeWriter to, int segmentId) {
		DynByteBuf ob = to.bw;
		int begin = ob.wIndex();

		byte code = switch (TypeCast.getDataCap(v.type.getActualType())) {
			default -> ILOAD;
			case 5 -> LLOAD;
			case 6 -> FLOAD;
			case 7 -> DLOAD;
			case 8 -> ALOAD;
		};
		if (store) code += 33;
		to.vars(code, v.slot);

		begin = ob.wIndex() - begin;
		assert length() == begin;
		return false;
	}

	@Override
	public int length() {
		int slot = v.slot;
		if (slot <= 3) return 1; // CODE
		else if (slot < 255) return 2; // CODE V1
		else return 4; // WIDE CODE V1 V2
	}

	@Override
	public String toString() { return (store?"Put ":"Get ")+v.name; }
}