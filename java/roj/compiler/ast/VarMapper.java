package roj.compiler.ast;

import roj.collect.BitSet;
import roj.collect.IntMap;
import roj.collect.IntervalPartition;
import roj.collect.IntervalPartition.Endpoint;
import roj.collect.IntervalPartition.Segment;
import roj.collect.ArrayList;
import roj.compiler.asm.Variable;

import java.util.List;

/**
 * 根据变量作用域分配可重用的ID
 * @author Roj233
 * @since 2022/2/23 15:27
 */
final class VarMapper {
	private final IntervalPartition<Variable> vars = new IntervalPartition<>(10, false, 100);

	private final BitSet freeId = new BitSet();
	private final List<Variable> tmp2 = new ArrayList<>();
	private int _id;

	public void add(Variable v) {tmp2.add(v);}
	public void reserve(int slot) {
		if (_id >= slot+1) return;
		if (_id < slot) freeId.addRange(_id, slot);
		freeId.remove(slot);
		_id = slot+1;
	}
	public int map() {
		var union = vars;
		union.clear();
		for (Variable v : tmp2) union.add(v);
		tmp2.clear();

		var freeId = this.freeId;
		var curVars = tmp2;

		IntMap<Variable> known = new IntMap<>();

		int id = _id, peakId = id;

		Segment[] list = union.getSegments();
		for (int j = 0; j < union.getSegmentCount(); j++) {
			Endpoint p = list[j].anchor();
			while (p != null) {
				Variable v = p.interval();
				if (p.isEnd()) {
					int size = v.type.rawType().length();

					if (v.slot == id+size-1) id -= size;
					else freeId.addRange(v.slot, v.slot+size);
				} else {
					curVars.add(p.interval());
				}
				p = p.next();
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
					freeId.removeRange(nextId, nextId+size);
					var prev = known.get(nextId);
					if (prev != null) prev.end = v.start;
					v.slot = nextId;
				}
				known.put(v.slot, v);

				if (peakId < id) peakId = id;
			}
			curVars.clear();
		}
		freeId.clear();
		vars.clear();

		return peakId;
	}
	public void clear() {tmp2.clear();freeId.clear();_id = 0;}
}
