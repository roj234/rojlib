package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.tree.insn.SwitchEntry;
import roj.collect.BSLowHeap;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class SwitchSegment extends Segment {
	byte code;
	private char length;

	public Label def;
	public List<SwitchEntry> targets;

	public boolean opt;

	int fv_bci;

	SwitchSegment(int code, Label def, List<SwitchEntry> targets, int origPos) {
		this.code = (byte) code;
		this.def = def;
		this.targets = targets;

		int pad = (4 - ((origPos + 1) & 3)) & 3;
		this.length = (char) (code == Opcodes.TABLESWITCH ? 1 + pad + 4 + 8 + (targets.size() << 2) : 1 + pad + 8 + (targets.size() << 3));
	}

	public SwitchSegment() { this(false); }
	public SwitchSegment(boolean optimizeToGoto) { this(0); this.opt = optimizeToGoto; }
	public SwitchSegment(int code) {
		this.code = (byte) code;
		this.targets = new BSLowHeap<>(null);
		this.length = 1;
	}

	public void branch(int number, Label label) { targets.add(new SwitchEntry(number, label)); }

	@Override
	public boolean put(CodeWriter to) {
		targets.sort(null);

		if (code == 0) findBestCode();
		if (targets.isEmpty() && opt) {
			to.bw.put(Opcodes.POP);
			int len = length;
			if (def == null) {
				length = 0;
			} else {
				JumpSegment seg = new JumpSegment(GOTO, def);
				seg.put(to);
				length = (char) seg.length();
			}
			return len != length;
		}

		DynByteBuf o = to.bw;
		int begin = o.wIndex();

		int self = fv_bci = to.bci;
		o.put(code);

		int pad = (4 - ((self+1) & 3)) & 3;
		while (pad-- > 0) o.put(0);

		List<SwitchEntry> m = targets;
		if (def == null) throw new NullPointerException("default分支");
		if (code == TABLESWITCH) {
			if (m.isEmpty()) throw new NullPointerException("switch没有分支");
			int lo = m.get(0).val;
			int hi = m.get(m.size() - 1).val;

			o.putInt(def.getValue() - self).putInt(lo).putInt(hi);
			for (int i = 0; i < m.size(); i++) o.putInt(m.get(i).getBci() - self);
		} else {
			o.putInt(def.getValue() - self).putInt(m.size());
			for (int i = 0; i < m.size(); i++) {
				SwitchEntry se = m.get(i);
				o.putInt(se.val).putInt(se.getBci() - self);
			}
		}

		begin = o.wIndex() - begin;
		if (length != begin) {
			length = (char) begin;
			return true;
		}
		return false;
	}
	@Override
	protected int length() { return length; }

	private void findBestCode() {
		List<SwitchEntry> m = targets;
		if (m.isEmpty()) {
			code = LOOKUPSWITCH;
			return;
		}

		int lo = m.get(0).val;
		int hi = m.get(m.size()-1).val;

		long tableSwitchSpaceCost = 4 * ((long) hi - lo + 1) + 12;
		int lookupSwitchSpaceCost = 4 + 8 * m.size();
		if (tableSwitchSpaceCost <= lookupSwitchSpaceCost) {
			code = TABLESWITCH;
			if (m.size() < hi-lo+1) {
				for (int i = lo; i <= hi; i++) m.add(new SwitchEntry(i, def));
			}
		} else {
			code = LOOKUPSWITCH;
		}
	}

	@Override
	Segment move(AbstractCodeWriter list, int blockMoved, int mode) {
		if (mode==XInsnList.REP_CLONE) {
			SwitchSegment next = new SwitchSegment();
			for (int i = 0; i < targets.size(); i++) {
				SwitchEntry entry = targets.get(i);
				Label label = copyLabel(entry.pos, list, blockMoved, XInsnList.REP_CLONE);
				entry = new SwitchEntry(entry.val, label);
				next.targets.add(entry);
			}
			next.def = copyLabel(def, list, blockMoved, XInsnList.REP_CLONE);
			return next;
		}

		for (int i = 0; i < targets.size(); i++) {
			copyLabel(targets.get(i).pos, list, blockMoved, mode);
		}
		copyLabel(def, list, blockMoved, mode);
		return this;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append(OpcodeUtil.toString0(code)).append("{");
		SimpleList<Object> a = SimpleList.asModifiableList("value","target",IntMap.UNDEFINED);
		for (SwitchEntry target : targets) {
			a.add(target.val);
			a.add(target.pos.getValue());
			a.add(IntMap.UNDEFINED);
		}
		a.add("default");
		a.add(def.getValue());
		a.add(IntMap.UNDEFINED);

		TextUtil.prettyTable(sb, "  ", a.toArray(), "  ");
		return sb.append('}').toStringAndFree();
	}
}
