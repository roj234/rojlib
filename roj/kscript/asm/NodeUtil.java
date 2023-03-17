package roj.kscript.asm;

import roj.collect.MyHashSet;
import roj.collect.RSegmentTree;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/28 18:32
 */
public class NodeUtil {
	public static VInfo calcDiff(List<RSegmentTree.Wrap<Variable>> self, List<RSegmentTree.Wrap<Variable>> dest) {
		MyHashSet<Variable> cvHas = new MyHashSet<>(self.size());
		VInfo root = new VInfo(), curr = root;
		// Pass 0
		for (int i = 0; i < self.size(); i++) {
			RSegmentTree.Wrap<Variable> wrap = self.get(i);
			cvHas.add(wrap.sth);
		}
		// Pass 1: check dest.add
		for (int i = 0; i < dest.size(); i++) {
			RSegmentTree.Wrap<Variable> wrap = dest.get(i);
			if (!cvHas.remove(wrap.sth)) {
				// append variable
				curr.id = wrap.sth.index;
				curr.v = wrap.sth.def;
				curr.next = new VInfo();
				curr = curr.next;
				//} else {
				//     duplicate variable, pass
			}
		}
		// Pass 2: check dest.remove
		for (Variable var : cvHas) {
			curr.id = var.index;
			curr.next = new VInfo();
			curr = curr.next;
		}
		// Pass 3: remove useless curr
		if (curr == root) return null;
		VInfo end = curr;
		curr = root;
		while (true) {
			if (curr.next == end) {
				curr.next = null;
				break;
			}
			curr = curr.next;
		}

		return root;
	}
}
