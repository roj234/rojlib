package roj.asm.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roj234
 * @since 2026/01/26 01:58
 */
@FunctionalInterface
public interface TypeVariableContext {
	/**
	 * 根据名字 A, B, T 寻找对应的 Declaration
	 * 并在不存在时返回null（或者随便你怎么干）
	 */
	@Nullable
	TypeVariableDeclaration resolveTypeVariable(String name);

	default TypeVariableContext withParent(@NotNull TypeVariableContext parent) {
		if (parent == EMPTY) return this;
		if (this == EMPTY) return parent;

		return name -> {
			var decl = this.resolveTypeVariable(name);
			if (decl != null) return decl;
			return parent.resolveTypeVariable(name);
		};
	}

	TypeVariableContext EMPTY = TypeVariableDeclaration::newUnresolved;
}
