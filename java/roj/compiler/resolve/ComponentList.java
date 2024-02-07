package roj.compiler.resolve;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.SimpleList;
import roj.compiler.context.CompileContext;

import java.util.Map;

/**
 * @author Roj234
 * @since 2024/2/6 2:51
 */
public abstract class ComponentList {
	public static final int IN_STATIC = 1, THIS_ONLY = 2;

	@Nullable
	public MethodResult findMethod(CompileContext ctx, IType generic, SimpleList<IType> params, Map<String, IType> namedType, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) { throw new UnsupportedOperationException("This list is a FieldList"); }
	@NotNull
	public FieldResult findField(CompileContext ctx, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) { throw new UnsupportedOperationException("This list is a MethodList"); }
}