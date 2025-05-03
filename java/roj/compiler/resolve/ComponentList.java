package roj.compiler.resolve;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Attributed;
import roj.asm.ClassDefinition;
import roj.asm.MethodNode;
import roj.asm.type.IType;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.config.Tokenizer;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2024/2/6 2:51
 */
public abstract class ComponentList {
	public static final int IN_STATIC = 1, THIS_ONLY = 2, NO_REPORT = 4;
	public static final ComponentList NOT_FOUND = new EmptyComponentList();

	@Nullable
	public final MethodResult findMethod(LocalContext ctx, List<IType> params, @MagicConstant(flags = {IN_STATIC, THIS_ONLY, NO_REPORT}) int flags) {return findMethod(ctx, null, params, Collections.emptyMap(), flags);}
	@Nullable
	public final MethodResult findMethod(LocalContext ctx, IType that, List<IType> params, @MagicConstant(flags = {IN_STATIC, THIS_ONLY, NO_REPORT}) int flags) {return findMethod(ctx, that, params, Collections.emptyMap(), flags);}
	/**
	 * @param that this类型
	 * @param params 参数数量及类型
	 * @param namedType 具名参数
	 * @param flags
	 */
	@Nullable
	public MethodResult findMethod(LocalContext ctx, IType that, List<IType> params, Map<String, IType> namedType, @MagicConstant(flags = {IN_STATIC, THIS_ONLY, NO_REPORT}) int flags) { throw new UnsupportedOperationException("这不是方法列表"); }
	@NotNull
	public FieldResult findField(LocalContext ctx, @MagicConstant(flags = {IN_STATIC, THIS_ONLY}) int flags) { throw new UnsupportedOperationException("这不是字段列表"); }

	public List<MethodNode> getMethods() {throw new UnsupportedOperationException("这不是方法列表");}
	public boolean isOverriddenMethod(int id) {return false;}

	static void checkDeprecation(LocalContext ctx, ClassDefinition owner, Attributed methodOrField) {
		if (owner.getRawAttribute("Deprecated") != null) {
			ctx.report(Kind.WARNING, "annotation.deprecated", "symbol.type", owner);
		}
		if (methodOrField.getRawAttribute("Deprecated") != null) {
			ctx.report(Kind.WARNING, "annotation.deprecated", "invoke.method", methodOrField);
		}
	}

	static BiConsumer<String, Object[]> makeErrorCapture(CharList sb) {
		return (translate, param) -> {
			sb.clear();
			sb.append(translate);
			if (param.length > 0) {
				sb.append(":[");
				for (Object o : param)
					Tokenizer.addSlashes(sb.append('"'), o.toString()).append('"');
				sb.append(']');
			}
		};
	}
}