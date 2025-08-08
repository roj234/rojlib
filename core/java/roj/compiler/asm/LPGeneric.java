package roj.compiler.asm;

import roj.asm.type.Generic;

/**
 * @author Roj234
 * @since 2022/9/17 20:37
 */
public final class LPGeneric extends Generic {
	public int pos;

	public LPGeneric() {}
	public LPGeneric(String owner) { this.owner = owner; }

	public boolean isGenericArray() {
		if (array() == 0) return false;
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i).genericType() != ANY_TYPE) return true;
		}
		return false;
	}
}