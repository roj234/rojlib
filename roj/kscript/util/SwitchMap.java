package roj.kscript.util;

import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.RSegmentTree;
import roj.kscript.asm.Frame;
import roj.kscript.asm.Node;
import roj.kscript.asm.NodeUtil;
import roj.kscript.type.KType;
import roj.kscript.util.opm.SWEntry;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/28 18:47
 */
public class SwitchMap extends MyHashMap<KType, Node> {
	public SwitchMap() {
		super(8);
	}

	public SwitchMap(int initCap) {
		super(initCap);
	}

	@Override
	protected Entry<KType, Node> createEntry(KType id) {
		return new SWEntry(id, null);
	}

	public void stripLabels() {
		Entry<?, ?>[] entries = this.entries;
		if (entries == null) return;
		for (int i = 0; i < length; i++) {
			SWEntry entry = (SWEntry) entries[i];
			if (entry == null) continue;
			while (entry != null) {
				entry.v = entry.v.next;
				entry = (SWEntry) entry.next;
			}
		}
	}

	public void genDiff(List<RSegmentTree.Wrap<Variable>> self, RSegmentTree<RSegmentTree.Wrap<Variable>> var, IntBiMap<Node> idx) {
		Entry<?, ?>[] entries = this.entries;
		if (entries == null) return;
		for (int i = 0; i < length; i++) {
			SWEntry entry = (SWEntry) entries[i];
			if (entry == null) continue;
			while (entry != null) {
				List<RSegmentTree.Wrap<Variable>> dest = var.collect(idx.getInt(entry.v));
				if (dest != self) {
					entry.diff = NodeUtil.calcDiff(self, dest);
				}
				entry = (SWEntry) entry.next;
			}
		}
	}

	public Node getAndApply(Frame frame, Node def, VInfo diff) {
		KType zero = frame.pop();
		SWEntry entry = (SWEntry) getEntryFirst(zero, false);
		while (entry != null) {
			if (zero.equalsTo(entry.k)) {
				frame.applyDiff(entry.diff);
				return entry.v;
			}
			entry = (SWEntry) entry.next;
		}

		frame.applyDiff(diff);
		return def;
	}
}
