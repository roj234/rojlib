package roj.compiler.ast;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.IntMap;
import roj.collect.SweepLine;
import roj.collect.SweepLine.Segment;
import roj.compiler.asm.Variable;

import java.util.List;

/**
 * 根据变量作用域分配可重用的ID
 * @author Roj233
 * @since 2022/2/23 15:27
 */
final class VarMapper {
	private final BitSet freeId = new BitSet();
	private final List<Variable> vars = new ArrayList<>();
	private int reservedPeak;

	public void add(Variable v) {vars.add(v);}
	public void reserve(int slot) {
		if (reservedPeak < slot) freeId.addRange(reservedPeak, slot);
		freeId.remove(slot);
		if (reservedPeak < slot + 1) reservedPeak = slot + 1;
	}
	public int map() {
		var freeId = this.freeId;
		var curVars = vars;
		var scans = SweepLine.scan(curVars, SweepLine.Range.getExtractor());
		var known = new IntMap<Variable>();
		curVars.clear();

		int id = reservedPeak, peakId = id;


		for (int j = 0; j < scans.size();) {
			var p = scans.get(j++);
			int currentPos = getPos(p);
			while (true) {
				Variable v = p.value;
				boolean end = p.isEnd;

				if (end) {
					if (curVars.remove(v)) continue;

					int size = v.type.rawType().length();

					if (v.slot == id+size-1) id -= size;
					else freeId.addRange(v.slot, v.slot+size);
				} else {
					curVars.add(v);
				}

				if (j >= scans.size()) break;

				p = scans.get(j);
				if (getPos(p) != currentPos) break;
				j++;
			}

			for (int i = 0; i < curVars.size(); i++) {
				var v = curVars.get(i);
				int size = v.type.rawType().length();

				int nextId = freeId.nextTrue(0);

				if (size > 1) {
					while (nextId > 0 && !freeId.contains(nextId+1)) {
						nextId = freeId.nextTrue(nextId+1);
					}
				}

				if (nextId < 0) {
					v.slot = id;
					id += size;
				} else {
					v.slot = nextId;
					freeId.removeRange(nextId, nextId+size);

					var prev = known.get(nextId);
					if (prev != null) prev.end = v.start;
				}
				known.put(v.slot, v);

				if (peakId < id) peakId = id;
			}
			curVars.clear();
		}
		freeId.clear();

		return peakId;
	}

	private static int getPos(Segment<Variable> p) {return (p.isEnd ? p.value.end : p.value.start).getValue();}

	public void clear() {vars.clear();freeId.clear();reservedPeak = 0;}
}
