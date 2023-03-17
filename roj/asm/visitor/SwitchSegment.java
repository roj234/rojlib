package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.tree.insn.SwitchEntry;
import roj.collect.BSLowHeap;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.TABLESWITCH;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class SwitchSegment extends Segment {
	public Label def;
	public List<SwitchEntry> targets;
	private final byte code;
	int bci;

	SwitchSegment(int code, Label def, List<SwitchEntry> targets, int origPos) {
		this.code = (byte) code;
		this.def = def;
		this.targets = targets;
		targets.sort(null);

		int pad = (4 - ((origPos + 1) & 3)) & 3;
		this.length = code == Opcodes.TABLESWITCH ? 1 + pad + 4 + 8 + (targets.size() << 2) : 1 + pad + 8 + (targets.size() << 3);
	}

	SwitchSegment(int code) {
		this.code = (byte) code;
		this.targets = new BSLowHeap<>(null);
		this.length = 1;
	}

	@Override
	public boolean put(CodeWriter to) {
		DynByteBuf o = to.bw;
		int begin = o.wIndex();

		int self = bci = to.bci;
		o.put(code);

		int pad = (4 - ((self+1) & 3)) & 3;
		while (pad-- > 0) o.put((byte) 0);

		List<SwitchEntry> m = targets;
		if (code == TABLESWITCH) {
			int lo = m.get(0).key;
			int hi = m.get(m.size() - 1).key;

			o.putInt(def.getValue() - self).putInt(lo).putInt(hi);
			for (int i = 0; i < m.size(); i++) o.putInt(m.get(i).getBci() - self);
		} else {
			o.putInt(def.getValue() - self).putInt(m.size());
			for (int i = 0; i < m.size(); i++) {
				SwitchEntry se = m.get(i);
				o.putInt(se.key).putInt(se.getBci() - self);
			}
		}

		begin = o.wIndex() - begin;
		if (length != begin) {
			length = begin;
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return "switch(" + OpcodeUtil.toString0(code) + ',' + length + ')';
	}
}
