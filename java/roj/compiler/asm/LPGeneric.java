package roj.compiler.asm;

import roj.asm.type.Generic;

/**
 * @author Roj234
 * @since 2022/9/17 0017 20:37
 */
public final class LPGeneric extends Generic {
	public int wrPos;

	public LPGeneric() {}
	public LPGeneric(String owner) { this.owner = owner; }

	public boolean isGenericArray() {
		if (array() == 0) return false;
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i).genericType() != ANY_TYPE) return true;
		}
		return false;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Generic generic)) return false;

		if (array() != generic.array()) return false;
		if (extendType != generic.extendType) return false;
		if (!owner.equals(generic.owner)) return false;
		if (sub != null ? !sub.equals(generic.sub) : generic.sub != null) return false;
		return childrenRaw().equals(generic.childrenRaw());
	}

	@Override
	public int hashCode() {
		int result = 0;
		result = 31 * result + owner.hashCode();
		result = 31 * result + (sub != null ? sub.hashCode() : 0);
		result = 31 * result + array();
		result = 31 * result + extendType;
		result = 31 * result + childrenRaw().hashCode();
		return result;
	}
}