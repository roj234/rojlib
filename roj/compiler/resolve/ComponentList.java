package roj.compiler.resolve;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.collect.SimpleList;
import roj.compiler.context.CompileContext;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.List;
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
	public FieldResult findField(CompileContext ctx, @MagicConstant(flags = {IN_STATIC,THIS_ONLY}) int flags) { throw new UnsupportedOperationException("This list is a MethodList"); }

	static void appendInput(List<IType> params, CharList sb) {
		sb.append("  ").append("{invoke.found} ");
		if (params.isEmpty()) sb.append("{invoke.no_param}");
		else sb.append(TextUtil.join(params, ","));
		sb.append('\n');
	}
	static CharList getArg(MethodNode mn, CharList sb) {
		Signature sign = mn.parsedAttr(null, Attribute.SIGNATURE);
		List<? extends IType> params2 = sign == null ? mn.parameters() : sign.values.subList(0, sign.values.size()-1);
		return sb.append(params2.isEmpty() ? "{invoke.no_param}" : TextUtil.join(params2, ","));
	}
}