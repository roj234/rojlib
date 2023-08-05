package roj.asm.frame.node;

import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Segment;
import roj.lavac.asm.Variable;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
public class LazyIINC extends Segment {
	private final Variable v;
	private final short amount;

	public LazyIINC(Variable v, int amount) {
		this.v = v;
		this.amount = (short) amount;
	}

	@Override
	protected boolean put(CodeWriter to) {
		DynByteBuf ob = to.bw;
		int begin = ob.wIndex();

		to.iinc(v.slot, amount);

		begin = ob.wIndex() - begin;
		assert length() == begin;
		return false;
	}
	@Override
	protected int length() { return v.slot > 255 || amount != (byte) amount ? 6 : 3; }
}
