package roj.lavac.asm;

import roj.asm.tree.Attributed;
import roj.asm.tree.Method;
import roj.asm.util.AttributeList;

public class MethodParamAnno implements Attributed {
	public MethodParamAnno(Method method, int idx, String name, int parNo) {

	}

	@Override
	public AttributeList attributes() {
		return new AttributeList();
	}

	@Override
	public char modifier() {
		return 0;
	}

	@Override
	public int type() {
		return -100;
	}
}
