package roj.compiler.resolve;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/2/6 2:57
 */
final class MethodListSingle extends ComponentList {
	final MethodNode node;
	final boolean isOverriden;
	MethodListSingle(MethodNode node, boolean overriddenMethod) { this.node = node; this.isOverriden = overriddenMethod; }
	@Override public List<MethodNode> getMethods() {return Collections.singletonList(node);}
	@Override public boolean isOverriddenMethod(int id) {return isOverriden;}
	@Override public String toString() {return "["+node.returnType()+' '+node.ownerClass()+'.'+node.name()+"("+TextUtil.join(node.parameters(), ", ")+")]";}

	public MethodResult findMethod(LocalContext ctx, IType that, List<IType> params,
								   Map<String, IType> namedType, int flags) {
		SimpleList<IType> myParam = null;
		MethodNode mn = node;
		var mnOwner = ctx.classes.getClassInfo(mn.owner);

		if (!ctx.checkAccessible(mnOwner, mn, (flags&IN_STATIC) != 0, true)) return null;

		MethodResult result = null;
		error: {
			List<Type> mParam = mn.parameters();
			IntMap<Object> defParamState = null;

			if (mParam.size() != params.size() || !namedType.isEmpty()) {
				var isVarargs = (mn.modifier & Opcodes.ACC_VARARGS) != 0;
				int defReq = mParam.size() - params.size() - namedType.size();
				if(!isVarargs && defReq < 0) break error;

				IntMap<String> mdvalue = ctx.getDefaultValue(mnOwner, mn);
				if (defReq > mdvalue.size()) break error;

				defParamState = new IntMap<>();
				myParam = new SimpleList<>(params);

				List<String> names = ParamNameMapper.getParameterName(mnOwner.cp(), mn);
				if (names.size() != mParam.size()) {
					ctx.report(Kind.ERROR, "invoke.warn.illegalNameList", mn);
					return null;
				} else {
					// 可能在Annotation Resolve阶段，此时tmpMap1可用，但2不行
					MyHashMap<String, IType> tmpMap1 = Helpers.cast(ctx.tmpMap1);
					tmpMap1.clear(); tmpMap1.putAll(namedType);

					for (int i = params.size(); i < mParam.size(); i++) {
						String name = names.get(i);
						IType type = tmpMap1.remove(name);
						if (type == null) {
							ExprNode c = ctx.parseDefaultValue(mdvalue.get(i));
							if (c == null) {
								if (i == mParam.size() - 1 && isVarargs) break;

								ctx.report(Kind.ERROR, "invoke.error.paramMissing", mnOwner, mn.name(), name);
								return null;
							}
							type = c.type();
							defParamState.putInt(i, c);
						} else {
							defParamState.putInt(i, name);
						}
						myParam.add(type);
					}

					if (!tmpMap1.isEmpty()) {
						ctx.report(Kind.ERROR, "invoke.error.paramExtra", mnOwner, mn.name(), tmpMap1);
						tmpMap1.clear();
						return null;
					}
				}
			}

			ctx.inferrer._minCastAllow = -1;
			result = ctx.inferrer.infer(mnOwner, mn, that, myParam == null ? params : myParam);
			ctx.inferrer._minCastAllow = 0;
			if (result.method != null) {
				result.namedParams = defParamState;
				MethodList.checkBridgeMethod(ctx, result);
				checkDeprecation(ctx, mnOwner, mn);
				return result;
			}
		}

		if ((flags & NO_REPORT) == 0) {
			if (result == null) result = ctx.inferrer.infer(mnOwner, mn, that, params);

			CharList sb = new CharList().append("invoke.incompatible.single\1").append(mn.owner).append("\0\1").append(mn.name()).append("\0\1");

			sb.append("  ").append("\1invoke.except\0 ");
			MethodList.getArg(mn, that, sb).append('\n');

			MethodList.appendInput(myParam == null ? params : myParam, sb);

			sb.append("  ").append("\1invoke.reason\0 \1");
			MethodList.appendError(result, sb);
			sb.append("\0\n");

			ctx.report(Kind.ERROR, sb.replace('/', '.').toStringAndFree());
		}
		return null;
	}
}