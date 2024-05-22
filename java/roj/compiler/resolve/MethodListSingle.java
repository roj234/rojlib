package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/2/6 2:57
 */
final class MethodListSingle extends ComponentList {
	final MethodNode node;
	MethodListSingle(MethodNode node) { this.node = node; }

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

			varargCheck:
			if (mParam.size() != params.size()) {
				if ((mn.modifier & Opcodes.ACC_VARARGS) != 0) break varargCheck;

				int defReq = mParam.size() - params.size() - namedType.size();
				if(defReq < 0) break error;

				IntMap<ExprNode> mdvalue = ctx.getDefaultValue(mnOwner, mn);
				if (defReq > mdvalue.size()) break error;

				defParamState = new IntMap<>();
				myParam = new SimpleList<>(params);

				List<String> names = ParamNameMapper.getParameterName(mnOwner.cp(), mn);

				if (names == null) {
					for (int i = params.size(); i < mParam.size(); i++) {
						ExprNode c = mdvalue.get(i);
						if (c == null) break error;
						defParamState.putInt(i, c);
						myParam.add(c.type());
					}
				} else if (names.size() != mParam.size()) {
					ctx.report(Kind.WARNING, "invoke.warn.illegalNameList", mn);
					break error;
				} else {
					for (int i = params.size(); i < mParam.size(); i++) {
						String name = names.get(i);
						IType type = namedType.get(name);
						if (type == null) {
							ExprNode c = mdvalue.get(i);
							if (c == null) break error;
							type = c.type();
							defParamState.putInt(i, c);
						} else {
							defParamState.putInt(i, name);
						}
						myParam.add(type);
					}
				}
			}

			result = ctx.inferrer.infer(mnOwner, mn, that, myParam == null ? params : myParam);
			if (result.distance >= 0) {
				result.namedParams = defParamState;
				MethodList.checkBridgeMethod(ctx, result);
				return result;
			}
		}

		if ((flags & NO_REPORT) != 0) return null;
		if (result == null) result = ctx.inferrer.infer(mnOwner, mn, that, params);

		CharList sb = new CharList().append("invoke.incompatible.single:").append(mn.owner).append(':').append(mn.name()).append(':');

		sb.append("  ").append("\1invoke.except\0 ");
		MethodList.getArg(mn, sb).append('\n');

		MethodList.appendInput(myParam == null ? params : myParam, sb);

		sb.append("  ").append("\1invoke.reason\0 ");
		MethodList.appendError(result, sb);
		sb.append('\n');

		ctx.report(Kind.ERROR, sb.replace('/', '.').toStringAndFree());
		return null;
	}

	@Override
	public List<MethodNode> getMethods() {return Collections.singletonList(node);}

	@Override
	public String toString() {return "["+node.ownerClass()+" => ("+TextUtil.join(node.parameters(), ", ")+") => "+node.returnType()+']';}
}