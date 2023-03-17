package roj.asm.nixim;

import roj.asm.tree.MoFNode;

/**
 * @author solo6975
 * @since 2021/10/3 20:59
 */
final class ShadowCheck extends RemapEntry {
	byte flag;

	ShadowCheck(MoFNode node) {
		super(node);
	}

	ShadowCheck() {}
}
