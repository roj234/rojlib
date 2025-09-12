package roj.compiler.asm;

import roj.asm.insn.CodeWriter;
import roj.asm.insn.Segment;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
final class LazyIINC extends Segment {
	private final Variable v;
	private final short amount;

	public LazyIINC(Variable v, int amount) {
		this.v = v;
		this.amount = (short) amount;
	}

	@Override
	public boolean write(CodeWriter to, int segmentId) {
		DynByteBuf ob = to.bw;
		int begin = ob.wIndex();

		to.iinc(v.slot, amount);

		begin = ob.wIndex() - begin;
		assert length() == begin;
		return false;
	}
	@Override
	public int length() { return v.slot > 255 || amount != (byte) amount ? 6 : 3; }

	@Override
	public String toString() { return "Incr(\""+v.name+"\", "+amount+")"; }
}