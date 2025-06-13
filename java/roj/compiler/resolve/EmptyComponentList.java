package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.MethodNode;
import roj.asm.type.IType;
import roj.compiler.CompileContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2025/1/24 22:10
 */
final class EmptyComponentList extends ComponentList {
	EmptyComponentList() {}

	@Override public MethodResult findMethod(CompileContext ctx, IType that, List<IType> params, Map<String, IType> namedType, int flags) {return null;}
	@Override public List<MethodNode> getMethods() {return Collections.emptyList();}
	@Override public @NotNull FieldResult findField(CompileContext ctx, int flags) {return new FieldResult("NoSuchField");}
}