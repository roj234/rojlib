package roj.compiler.api_rt;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.IClass;
import roj.compiler.resolve.ComponentList;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:58
 */
public interface ResolveApi {
	void addTypeResolver(int priority, TypeResolver resolver);
	void addIdentifierResolver(String type, boolean parent, IdentifierResolver listener);

	interface IdentifierResolver {
		@Nullable ComponentList wrap(IClass type, String identifier, boolean method, boolean staticEnv, @Nullable ComponentList list);
	}

	interface TypeResolver {
		@Nullable IClass resolve(CharSequence name);
	}
}