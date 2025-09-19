package roj.compiler.asm;

import roj.asm.type.ParameterizedType;

/**
 * @author Roj234
 * @since 2022/9/17 20:37
 */
public final class LavaParameterizedType extends ParameterizedType {
	public int pos;

	public LavaParameterizedType() {}
	public LavaParameterizedType(String owner) { this.owner = owner; }

	public boolean isGenericArray() {
		if (array() == 0) return false;
		for (int i = 0; i < typeParameters.size(); i++) {
			if (typeParameters.get(i).kind() != UNBOUNDED_WILDCARD) return true;
		}
		return false;
	}
}