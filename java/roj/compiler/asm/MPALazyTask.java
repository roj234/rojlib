package roj.compiler.asm;

import roj.asm.tree.Attributed;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttributeList;

public class MPALazyTask implements Attributed {
	public MPALazyTask(MethodNode method, int wPos, String wVal, int parNo) {

	}

	@Override
	public AttributeList attributes() {
		return new AttributeList();
	}

	@Override
	public char modifier() { return 0; }
}