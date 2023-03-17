package roj.asm.nixim;

import roj.asm.cst.CstRef;
import roj.asm.tree.MoFNode;

/**
 * @author solo6975
 * @since 2021/10/3 20:59
 */
class RemapEntry extends DescEntry {
	String toClass, toName;

	public final RemapEntry read(CstRef ref) {
		this.name = ref.descName();
		this.desc = ref.descType();
		return this;
	}

	RemapEntry() {}

	RemapEntry(MoFNode node) {
		super(node);
	}
}
