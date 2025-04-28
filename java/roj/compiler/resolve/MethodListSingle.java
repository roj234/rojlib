package roj.compiler.resolve;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.ast.expr.Expr;
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
	final MethodNode method;
	final boolean isOverriden;
	MethodListSingle(MethodNode method, boolean overriddenMethod) { this.method = method; this.isOverriden = overriddenMethod; }
	@Override public List<MethodNode> getMethods() {return Collections.singletonList(method);}
	@Override public boolean isOverriddenMethod(int id) {return isOverriden;}
	@Override public String toString() {return "["+method.returnType()+' '+method.ownerClass()+'.'+method.name()+"("+TextUtil.join(method.parameters(), ", ")+")]";}

	public MethodResult findMethod(LocalContext ctx, IType that, List<IType> actualArguments,
								   Map<String, IType> namedArguments, int flags) {
		MethodNode method = this.method;
		ClassNode owner = ctx.classes.getClassInfo(method.owner);

		if (!ctx.checkAccessible(owner, method, (flags&IN_STATIC) != 0, true)) return null;

		MethodResult result = null;
		error: {
			List<Type> declaredArguments = method.parameters();
			IntMap<Object> fillArguments = null;

			int missedArguments = declaredArguments.size() - actualArguments.size();
			maybeCorrect:
			if (missedArguments != 0) {
				var isVarargs = (method.modifier & Opcodes.ACC_VARARGS) != 0;
				if (missedArguments < 0) {
					// 普通方法，参数多了一定不匹配
					if (!isVarargs) break error;
					break maybeCorrect;
				}
				// 但是可变参数方法能多，甚至还可以少1个
				// 可变参数方法的最后一个参数（可变参数）不能具有默认值，所以在这里就不调用下面比较复杂的部分了
				if (missedArguments == 1 && isVarargs && namedArguments.isEmpty()) break maybeCorrect;

				// 现在一定缺少参数

				// 仅读取字符串，不反序列化
				IntMap<String> defaultArguments = ctx.getDefaultArguments(owner, method);
				// 如果加起来都不够，那么一定不够
				if (namedArguments.size() + defaultArguments.size() < missedArguments) break error;

				List<String> paramNames = ParamNameMapper.getParameterNames(owner.cp(), method);
				if (paramNames.size() != declaredArguments.size()) {
					ctx.report(Kind.INTERNAL_ERROR, "invoke.warn.illegalNameList", method);
					return null;
				}

				fillArguments = new IntMap<>();
				actualArguments = new SimpleList<>(actualArguments);

				// 可能在Annotation Resolve阶段，此时tmpMap1可用，但2不行
				MyHashMap<String, IType> tmp = Helpers.cast(ctx.tmpMap1); tmp.clear();
				tmp.putAll(namedArguments);
				namedArguments = tmp;

				// 填充缺少的参数
				for (int i = actualArguments.size(); i < declaredArguments.size(); i++) {
					String paramName = paramNames.get(i);
					IType argType = namedArguments.remove(paramName);
					if (argType == null) {
						// 使用参数默认值
						Expr parsed = ctx.parseDefaultArgument(defaultArguments.get(i));
						if (parsed == null) {
							// 可变参数，如果没有参数调用
							if (isVarargs && i == declaredArguments.size() - 1) break;

							//弃用，按"参数数量不同"错误走error分支
							//ctx.report(Kind.ERROR, "invoke.error.paramMissing", owner, method.name(), paramName);
							break error;
						}

						argType = parsed.type();
						fillArguments.putInt(i, parsed);
					} else {
						// 使用参数调用
						fillArguments.putInt(i, paramName);
					}
					actualArguments.add(argType);
				}
			}

			// 如果参数调用有剩的
			if (!namedArguments.isEmpty()) {
				ctx.report(Kind.ERROR, "invoke.error.paramExtra", owner, method.name(), namedArguments);
				ctx.tmpMap1.clear(); // GC
				return null;
			}

			ctx.inferrer._minCastAllow = -1;
			result = ctx.inferrer.infer(owner, method, that, actualArguments);
			ctx.inferrer._minCastAllow = 0;
			if (result.method != null) {
				result.filledArguments = fillArguments;
				MethodList.checkBridgeMethod(ctx, result);
				checkDeprecation(ctx, owner, method);
				return result;
			}
		}

		if ((flags & NO_REPORT) == 0) {
			if (result == null) result = ctx.inferrer.infer(owner, method, that, actualArguments);

			CharList sb = new CharList().append("invoke.incompatible.single:[\"").append(method.owner).append("\",[");
			if (method.name().equals("<init>")) sb.append("invoke.constructor],[");
			else sb.append("invoke.method,\" ").append(method.name()).append("\"],[");

			sb.append("\"  \",invoke.except,\" \",");
			MethodList.getArg(method, that, sb).append("\"\n\",");

			MethodList.appendInput(actualArguments, sb);

			sb.append("\"  \",invoke.reason,\" \",");
			MethodList.appendError(result, sb);
			sb.append("\"\n\"]");

			ctx.report(Kind.ERROR, sb.replace('/', '.').toStringAndFree());
		}
		return null;
	}
}