package roj.asm.util;

import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.collect.IntBiMap;
import roj.collect.SimpleList;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class InsnList extends SimpleList<InsnNode> {
	static final long serialVersionUID = 0L;

	SimpleList<InsnNode> labels = new SimpleList<>();

	@Override
	public boolean add(InsnNode node) {
		if (node instanceof LabelInsnNode) {
			labels.add(node);
			return true;
		} else {
			for (int i = 0; i < labels.size(); i++) {
				labels.get(i)._i_replace(node);
			}
			labels.clear();
			return super.add(node);
		}
	}

	@Override
	public void add(int i, InsnNode node) {
		if (node instanceof LabelInsnNode) {
			throw new IllegalArgumentException("Label insert not rewindable");
		} else {
			if (i == size) {
				for (int j = 0; j < labels.size(); j++) {
					labels.get(j)._i_replace(node);
				}
				labels.clear();
			}
			super.add(i, node);
		}
	}

	public IntBiMap<InsnNode> getPCMap() {
		IntBiMap<InsnNode> rev = new IntBiMap<>();
		int bci = 0;
		for (int i = 0; i < size(); i++) {
			InsnNode node = get(i);
			rev.putInt(bci, node);
			node.bci = (char) bci;
			bci += node.nodeSize(node.bci);
		}
		return rev;
	}
}
