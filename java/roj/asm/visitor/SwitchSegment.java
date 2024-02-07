package roj.asm.visitor;

import roj.asm.Opcodes;
import roj.asm.tree.insn.SwitchEntry;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
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

	public int fv_bci;

	SwitchSegment(int code, Label def, List<SwitchEntry> targets, int origPos) {
		this.code = (byte) code;
		this.def = def;
		this.targets = targets;

		int pad = (4 - ((origPos + 1) & 3)) & 3;
		this.length = (char) (code == TABLESWITCH ? 1 + pad + 4 + 8 + (targets.size() << 2) : 1 + pad + 8 + (targets.size() << 3));
	}

	public SwitchSegment() { this(0); }
	public SwitchSegment(int code) {
		this.code = (byte) code;
		this.targets = new SimpleList<>();
		this.length = 1;
	}

	public void branch(int number, Label label) { targets.add(new SwitchEntry(number, label)); }

	@Override
	public boolean put(CodeWriter to, int segmentId) {
		targets.sort(null);

		if (code == 0) {
			if (targets.isEmpty()) {
				to.bw.put(Opcodes.POP);
				int len = length;
				if (def == null) {
					length = 0;
				} else {
					JumpSegment seg = new JumpSegment(GOTO, def);
					seg.put(to, segmentId);
					length = (char) seg.length();
				}
				return len != length;
			}
			findBestCode();
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
	public int length() { return length; }

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
	public Segment move(AbstractCodeWriter from, AbstractCodeWriter to, int blockMoved, int mode) {
		if (mode==XInsnList.REP_CLONE) {
			SwitchSegment next = new SwitchSegment();
			for (int i = 0; i < targets.size(); i++) {
				SwitchEntry entry = targets.get(i);
				Label label = copyLabel(entry.pos, from, to, blockMoved, XInsnList.REP_CLONE);
				entry = new SwitchEntry(entry.val, label);
				next.targets.add(entry);
			}
			next.def = copyLabel(def, from, to, blockMoved, XInsnList.REP_CLONE);
			return next;
		}

		for (int i = 0; i < targets.size(); i++) {
			copyLabel(targets.get(i).pos, from, to, blockMoved, mode);
		}
		copyLabel(def, from, to, blockMoved, mode);
		return this;
	}

	@Override
	public boolean isTerminate() { return true; }

	@Override
	public boolean willJumpTo(int block, int offset) {
		if (def.offset == offset && def.block == block) return true;
		for (int i = 0; i < targets.size(); i++) {
			SwitchEntry target = targets.get(i);
			if (target.pos.offset == offset && target.pos.block == block) return true;
		}
		return false;
	}

	@Override
	public String toString() { return toString(IOUtil.getSharedCharBuf().append(Opcodes.showOpcode(code)).append(' '), 0).toString(); }

	public CharList toString(CharList sb, int prefix) {
		sb.append("{");
		SimpleList<Object> a = new SimpleList<>();
		for (SwitchEntry target : targets) {
			a.add(target.val);
			a.add(target.pos.getValue());
			a.add(IntMap.UNDEFINED);
		}
		a.add("default");
		a.add(def.getValue());
		a.add(IntMap.UNDEFINED);

		TextUtil.prettyTable(sb, new CharList().padEnd(' ', prefix+4).toStringAndFree(), a.toArray(), "  ");
		return sb.padEnd(' ', prefix).append('}');
	}
}