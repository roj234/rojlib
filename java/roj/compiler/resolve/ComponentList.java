package roj.compiler.resolve;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.compiler.context.LocalContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/2/6 2:51
 */
public abstract class ComponentList {
	public static final int IN_STATIC = 1, THIS_ONLY = 2, NO_REPORT = 4;

	@Nullable
	public final MethodResult findMethod(LocalContext ctx, List<IType> params, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) {return findMethod(ctx, null, params, Collections.emptyMap(), flags);}
	@Nullable
	public final MethodResult findMethod(LocalContext ctx, IType generic, List<IType> params, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) {return findMethod(ctx, generic, params, Collections.emptyMap(), flags);}
	/**
	 * @param generic this类型
	 * @param params 参数数量及类型
	 * @param namedType 具名参数
	 * @param flags
	 */
	@Nullable
	public MethodResult findMethod(LocalContext ctx, IType generic, List<IType> params, Map<String, IType> namedType, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) { throw new UnsupportedOperationException("This list is a FieldList"); }
	@NotNull
	public FieldResult findField(LocalContext ctx, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) { throw new UnsupportedOperationException("This list is a MethodList"); }

	public List<MethodNode> getMethods() {throw new UnsupportedOperationException(getClass().getName()+" is not MethodList");}
}