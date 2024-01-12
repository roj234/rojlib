package roj.compiler.asm;

import roj.asm.tree.Attributed;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttributeList;

public class MethodParamAnno implements Attributed {
	public MethodParamAnno(MethodNode method, int idx, String name, int parNo) {

	}

	@Override
	public AttributeList attributes() {
		return new AttributeList();
	}

	@Override
	public char modifier() { return 0; }
}