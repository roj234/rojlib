package roj.asm.insn;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.Opcodes;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.collect.IntSet;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2022/11/17 12:53
 */
public final class SwitchBlock extends Segment {
	byte code;
	private char length;

	public Label def;
	public List<SwitchTarget> targets;

	public int fv_bci;

	SwitchBlock(int code, Label def, List<SwitchTarget> targets, int origPos) {
		this.code = (byte) code;
		this.def = def;
		this.targets = targets;

		int pad = (4 - ((origPos + 1) & 3)) & 3;
		this.length = (char) (code == TABLESWITCH ? 1 + pad + 4 + 8 + (targets.size() << 2) : 1 + pad + 8 + (targets.size() << 3));
	}

	SwitchBlock() {this((byte) 0);}
	SwitchBlock(byte code) {
		this.code = code;
		this.targets = new ArrayList<>();
		this.length = 1;
	}
	public static SwitchBlock ofAuto() {return new SwitchBlock();}
	public static SwitchBlock ofSwitch(@MagicConstant(intValues = {TABLESWITCH, LOOKUPSWITCH, 0}) byte code) {return new SwitchBlock(code);}

	public void branch(int number, Label label) { targets.add(new SwitchTarget(number, label)); }

	@Override
	public boolean write(CodeWriter to, int segmentId) {
		targets.sort(null);

		if (code == 0) {
			if (targets.isEmpty()) {
				to.bw.put(Opcodes.POP);
				int len = length;
				if (def == null) {
					length = 0;
				} else {
					JumpTo seg = new JumpTo(GOTO, def);
					seg.write(to, segmentId);
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

		List<SwitchTarget> m = targets;
		if (def == null) throw new NullPointerException("default分支");
		if (code == TABLESWITCH) {
			if (m.isEmpty()) throw new NullPointerException("switch没有分支");
			int lo = m.get(0).value;
			int hi = m.get(m.size() - 1).value;

			o.putInt(def.getValue() - self).putInt(lo).putInt(hi);
			for (int i = 0; i < m.size(); i++) o.putInt(m.get(i).bci() - self);
		} else {
			o.putInt(def.getValue() - self).putInt(m.size());
			for (int i = 0; i < m.size(); i++) {
				SwitchTarget se = m.get(i);
				o.putInt(se.value).putInt(se.bci() - self);
			}
		}

		begin = o.wIndex() - begin;
		if (length != begin) {
			length = (char) begin;
			return true;
		}
		return false;
	}
	@Override public int length() { return length; }

	public byte findBestCode() {
		List<SwitchTarget> m = targets;
		if (m.isEmpty()) {
			return code = LOOKUPSWITCH;
		}

		int lo = m.get(0).value;
		int hi = m.get(m.size()-1).value;

		long tableSwitchSpaceCost = 4 * ((long) hi - lo + 1) + 12;
		int lookupSwitchSpaceCost = 4 + 8 * m.size();
		if (tableSwitchSpaceCost <= lookupSwitchSpaceCost) {
			if (m.size() < hi-lo+1) {
				IntSet set = new IntSet();
				for (int i = 0; i < m.size(); i++) set.add(m.get(i).value);
				for (int i = lo; i <= hi; i++) {
					if (!set.contains(i)) m.add(new SwitchTarget(i, def));
				}
				m.sort(null);
			}
			return code = TABLESWITCH;
		} else {
			return code = LOOKUPSWITCH;
		}
	}

	@Override public boolean isTerminate() {return true;}
	@Override public boolean willJumpTo(int block, int offset) {
		if (def.offset == offset && def.block == block) return true;
		for (int i = 0; i < targets.size(); i++) {
			SwitchTarget target = targets.get(i);
			if ((offset == -1 || target.target.offset == offset) && target.target.block == block) return true;
		}
		return false;
	}

	@Override
	public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		if (clone) {
			var copy = ofSwitch(code);
			for (int i = 0; i < targets.size(); i++) {
				SwitchTarget entry = targets.get(i);
				copy.targets.add(new SwitchTarget(entry.value, copyLabel(entry.target, to, blockMoved, true)));
			}
			copy.def = copyLabel(def, to, blockMoved, true);
			return copy;
		}

		for (int i = 0; i < targets.size(); i++) {
			copyLabel(targets.get(i).target, to, blockMoved, false);
		}
		copyLabel(def, to, blockMoved, false);

		return this;
	}

	@Override
	public String toString() { return toString(IOUtil.getSharedCharBuf().append(Opcodes.toString(code)).append(' '), 0).toString(); }
	public CharList toString(CharList sb, int prefix) {
		sb.append("{");
		ArrayList<Object> a = new ArrayList<>((targets.size()+1)*3);
		a.add("default");
		a.add(def);
		a.add(IntMap.UNDEFINED);
		for (SwitchTarget target : targets) {
			a.add(target.value);
			a.add(target.target);
			a.add(IntMap.UNDEFINED);
		}

		TextUtil.prettyTable(sb, new CharList().padEnd(' ', prefix+4).toStringAndFree(), a.toArray(), "  ");
		return sb.padEnd(' ', prefix).append('}');
	}
}