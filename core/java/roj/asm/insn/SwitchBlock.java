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
	public List<SwitchCase> cases;

	private int bci;
	// 仅供FrameVisitor使用
	public int bci() {return bci;}

	SwitchBlock(int code, Label def, List<SwitchCase> cases, int origPos) {
		this.code = (byte) code;
		this.def = def;
		this.cases = cases;

		int pad = (4 - ((origPos + 1) & 3)) & 3;
		this.length = (char) (code == TABLESWITCH ? 1 + pad + 4 + 8 + (cases.size() << 2) : 1 + pad + 8 + (cases.size() << 3));
	}

	SwitchBlock() {this((byte) 0);}
	SwitchBlock(byte code) {
		this.code = code;
		this.cases = new ArrayList<>();
		this.length = 1;
	}
	public static SwitchBlock ofAuto() {return new SwitchBlock();}
	public static SwitchBlock ofSwitch(@MagicConstant(intValues = {TABLESWITCH, LOOKUPSWITCH, 0}) byte code) {return new SwitchBlock(code);}

	public void branch(int number, Label label) { cases.add(new SwitchCase(number, label)); }

	@Override
	public boolean write(CodeWriter to, int segmentId) {
		cases.sort(null);

		if (code == 0) {
			if (cases.isEmpty()) {
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

		int self = bci = to.bci;
		o.put(code);

		int pad = (4 - ((self+1) & 3)) & 3;
		while (pad-- > 0) o.put(0);

		List<SwitchCase> m = cases;
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
				SwitchCase se = m.get(i);
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
		List<SwitchCase> m = cases;
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
					if (!set.contains(i)) m.add(new SwitchCase(i, def));
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
		for (int i = 0; i < cases.size(); i++) {
			SwitchCase target = cases.get(i);
			if ((offset == -1 || target.target.offset == offset) && target.target.block == block) return true;
		}
		return false;
	}

	@Override
	public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		if (clone) {
			var copy = ofSwitch(code);
			for (int i = 0; i < cases.size(); i++) {
				SwitchCase entry = cases.get(i);
				copy.cases.add(new SwitchCase(entry.value, copyLabel(entry.target, to, blockMoved, true)));
			}
			copy.def = copyLabel(def, to, blockMoved, true);
			return copy;
		}

		for (int i = 0; i < cases.size(); i++) {
			copyLabel(cases.get(i).target, to, blockMoved, false);
		}
		copyLabel(def, to, blockMoved, false);

		return this;
	}

	@Override
	public String toString() { return toString(IOUtil.getSharedCharBuf().append(Opcodes.toString(code)).append(' '), 0).toString(); }
	public CharList toString(CharList sb, int prefix) {
		sb.append("{");
		ArrayList<Object> a = new ArrayList<>((cases.size()+1)*3);
		a.add("default");
		a.add(def);
		a.add(IntMap.UNDEFINED);
		for (SwitchCase target : cases) {
			a.add(target.value);
			a.add(target.target);
			a.add(IntMap.UNDEFINED);
		}

		TextUtil.prettyTable(sb, new CharList().padEnd(' ', prefix+4).toStringAndFree(), a.toArray(), "  ");
		return sb.padEnd(' ', prefix).append('}');
	}
}