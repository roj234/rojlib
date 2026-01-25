package roj.compiler.resolve;

import roj.asm.ClassNode;
import roj.asm.ClassUtil;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.*;
import roj.asmx.ParamNameMapper;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.IntMap;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Compiler;
import roj.compiler.api.InvokeHook;
import roj.compiler.ast.expr.Expr;
import roj.compiler.diagnostic.IText;
import roj.compiler.diagnostic.Kind;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.compiler.diagnostic.IText.*;

/**
 * @author Roj234
 * @since 2024/1/28 5:41
 */
final class MethodList extends ComponentList {
	ClassNode owner;
	final ArrayList<MethodNode> methods = new ArrayList<>();
	private int childId;
	private HashMap<String, List<MethodNode>> ddtmp = new HashMap<>();
	private BitSet overrider;

	void add(ClassNode klass, MethodNode mn) {
		// 忽略改变返回类型的重载的parent (a.k.a 如果有对应的桥接方法，就不去父类查询了)
		var list = ddtmp.computeIfAbsent(Type.getMethodDescriptor(mn.parameters()), Helpers.fnArrayList());
		for (int i = 0; i < list.size(); i++) {
			var prev = list.get(i);
			if (ClassUtil.isOverridable(klass.name(), mn.modifier, prev.owner())) {
				if (overrider == null) overrider = new BitSet();
				overrider.add(methods.indexOfAddress(prev));
				return;
			}
		}
		list.add(mn);

		methods.add(mn);
		mn.getAttribute(klass, Attribute.SIGNATURE);
	}

	/**
	 * @param klass 所有者
	 * @return 是否可以压缩为MethodListSingle
	 */
	boolean pack(ClassNode klass) {
		ddtmp = null;
		owner = klass;

		// 重载太多了，用查找表，但是懒加载，直到第一次findMethod的时候
		// 不过感觉用一个简单的结构不好filter，所以先咕咕咕
		// if (methods.size() > 10) lookupFlag = -128;

		String owner = klass.name();
		for (int i = 0; i < methods.size(); i++) {
			if (!owner.equals(methods.get(i).owner())) {
				childId = i;
				return false;
			}
		}
		childId = methods.size();
		return childId == 1;
	}

	@Override public boolean isOverriddenMethod(int id) {return overrider != null && overrider.contains(id);}
	@Override public List<MethodNode> getMethods() {return methods;}

	public MethodResult findMethod(CompileContext ctx, IType that, final List<IType> actualArguments,
								   final Map<String, IType> namedArguments, int flags) {
		ArrayList<MethodNode> candidates = methods;

		MethodResult best = null;
		List<MethodNode> dup = new ArrayList<>();

		int size = (flags&THIS_ONLY) != 0 ? childId : candidates.size();

		for (int j = 0; j < size; j++) {
			MethodNode method = candidates.get(j);
			var owner = ctx.compiler.resolve(method.owner());

			if (!ctx.canAccessSymbol(owner, method, (flags&IN_STATIC) != 0, false)) continue;

			List<Type> declaredArguments = method.parameters();
			IntMap<Object> fillArguments = null;
			ArrayList<IType> myParam = null;

			int missedArguments = declaredArguments.size() - actualArguments.size();
			dontCheckForNamedArguments: {
			maybeCorrect:
			if (missedArguments != 0) {
				var isVarargs = (method.modifier & Opcodes.ACC_VARARGS) != 0;
				if (missedArguments < 0) {
					// 普通方法，参数多了一定不匹配
					if (!isVarargs) continue;
					break maybeCorrect;
				}
				// 但是可变参数方法能多，甚至还可以少1个
				// 可变参数方法的最后一个参数（可变参数）不能具有默认值，所以在这里就不调用下面比较复杂的部分了
				if (missedArguments == 1 && isVarargs && namedArguments.isEmpty()) break maybeCorrect;

				// 现在一定缺少参数

				// 仅读取字符串，不反序列化
				IntMap<String> defaultArguments = ctx.getDefaultArguments(this.owner, method);
				// 如果加起来都不够，那么一定不够
				if (namedArguments.size() + defaultArguments.size() < missedArguments) continue;

				List<String> paramNames = ParamNameMapper.getParameterNames(this.owner.cp, method);
				if (paramNames.size() != declaredArguments.size()) {
					ctx.report(Kind.INTERNAL_ERROR, "invoke.warn.illegalNameList", method);
					return null;
				}

				fillArguments = new IntMap<>();
				myParam = new ArrayList<>(actualArguments);

				// 可能在Annotation Resolve阶段，此时tmpMap1可用，但2不行
				HashMap<String, IType> tmpMap = Helpers.cast(ctx.tmpMap1); tmpMap.clear();
				tmpMap.putAll(namedArguments);

				// 填充缺少的参数
				for (int i = actualArguments.size(); i < declaredArguments.size(); i++) {
					String paramName = paramNames.get(i);
					IType argType = tmpMap.remove(paramName);
					if (argType == null) {
						// 使用参数默认值
						Expr parsed = ctx.parseDefaultArgument(defaultArguments.get(i));
						if (parsed == null) {
							// 可变参数，如果没有参数调用
							if (isVarargs && i == declaredArguments.size() - 1) break;

							//弃用，按"参数数量不同"错误走error分支
							//ctx.report(Kind.ERROR, "invoke.paramMissing", owner, method.name(), paramName);
							continue;
						}

						argType = parsed.type();
						fillArguments.put(i, parsed);
					} else {
						// 使用参数调用
						fillArguments.put(i, paramName);
					}
					myParam.add(argType);
				}

				if (!tmpMap.isEmpty()) continue;
				break dontCheckForNamedArguments;
			}
			// 如果参数调用有剩的
			if (!namedArguments.isEmpty()) continue;
			}

			var result = ctx.inferrer.resolveInvocation(owner, method, that, myParam == null ? actualArguments : myParam);
			if (result.method == null) continue;

			int score = result.distance;
			if (best == null || score <= best.distance) {
				if (best != null && score == best.distance) dup.add(method);
				else {
					dup.clear();
					best = result;
					best.filledArguments = fillArguments;
				}
			}
		}


		String name = methods.get(0).name();
		boolean isConstructor = name.equals("<init>");
		if (isConstructor) name = owner.name().substring(owner.name().lastIndexOf('/')+1);

		if (!dup.isEmpty()) {
			IText infoList = getFoundText(actualArguments).prepend("  ").append("\n  ");

			infoList.append(renderMethod(best.method, isConstructor));

			for (MethodNode mn : dup) {
				infoList.append(translatable("and")).append("\n  ").append(renderMethod(mn, isConstructor));
			}
			infoList.append(translatable("invoke.matches"));

			// ambiguous
			ctx.report(Kind.ERROR, "invoke.compatible.plural", name, infoList);
		} else if (best == null) {
			// 不匹配 (No match)
			if ((flags & NO_REPORT) != 0) return null;

			// 参数列表输入
			IText inputPart = literal(name).append("(").append(renderParameters(actualArguments)).append(")");

			// 候选者列表
			IText candidateList = empty();
			ctx.enableErrorCapture();
			for (int i = 0; i < size; i++) {
				MethodNode mn = methods.get(i);

				IText line = literal("  ").append(renderMethod(mn, isConstructor))
					.append(translatable("invoke.notApplicable")).append("\n    (");

				var info = ctx.compiler.resolve(mn.owner());
				if (ctx.canAccessSymbol(info, mn, (flags & IN_STATIC) != 0, true)) {
					line.append(getReason(mn, that, ctx.inferrer.resolveInvocation(info, mn, that, actualArguments)));
				}

				IText captured = ctx.getCapturedError();
				if (captured != null) line.append(captured);

				line.append(")\n");
				candidateList.append(line);
			}
			ctx.disableErrorCapture();

			ctx.report(Kind.ERROR, "invoke.incompatible.plural", inputPart, candidateList);
		} else {
			checkDeprecation(ctx, owner, best.method);
			checkBridgeMethod(ctx, best);
		}

		return best;
	}

	static IText getFoundText(List<IType> params) {return translatable("invoke.found").append(renderParameters(params));}

	static IText renderMethod(MethodNode mn, boolean isConstructor) {
		String owner = mn.owner().replace('/', '.');
		String sign = isConstructor ? "" : "." + mn.name();
		return translatable(isConstructor ? "invoke.constructor" : "invoke.method")
				.append(owner).append(sign)
				.append("(").append(renderParameters(mn)).append(")");
	}
	static IText renderParameters(MethodNode mn) {
		Signature sign = mn.getAttribute(null, Attribute.SIGNATURE);
		List<? extends IType> params = sign == null ? mn.parameters() : sign.values.subList(0, sign.values.size()-1);
		return renderParameters(params);
	}
	static IText renderParameters(List<? extends IType> params) {
		if (params.isEmpty()) return translatable("invoke.no_param");

		IText root = IText.empty();
		int i = 0;
		while (true) {
			root.append(literal(params.get(i)));
			if (++i == params.size()) break;
			root.append(", ");
		}

		return root;
	}

	static IText getReason(MethodNode mn, IType that, MethodResult mr) {
		if (mr.distance > 0) return IText.empty();

		Map<TypeVariableDeclaration, IType> substitutionMap = Collections.emptyMap();
		if (that instanceof ParameterizedType g) {
			List<IType> typeParams = g.typeParameters;
			Signature signature = CompileContext.get().resolve(mn.owner()).getAttribute(Attribute.SIGNATURE);
			if (signature != null) {
				substitutionMap = Inferrer.createSubstitutionMap(signature.typeVariables, typeParams);
			}
		}

		String key = "typeCast.error."+mr.distance;
		if (mr.error != null && mr.error.length == 3) {
			Object left = mr.error[0];
			Object right = mr.error[1];
			if (left instanceof IType type) {
				left = Inferrer.substituteTypeVariables(type, substitutionMap);
			}
			if (right instanceof IType type) {
				right = Inferrer.substituteTypeVariables(type, substitutionMap);
			}

			return translatable("invoke.paramIndex", (int) (mr.error[2]) + 1).append(translatable(key, left, right));
		}
		return translatable(key);
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append('[');

		for (MethodNode node : methods) {
			sb.append(node.returnType()).append(' ').append(node.owner()).append('.').append(node.name()).append('(').append(TextUtil.join(node.parameters(), ", ")).append("), ");
		}

		return sb.append(']').toStringAndFree();
	}

	static void checkBridgeMethod(CompileContext ctx, MethodResult mr) {
		var mn = mr.method;
		if ((mn.modifier&Opcodes.ACC_PRIVATE) == 0 || ctx.file.name().equals(mn.owner()) ||
			ctx.compiler.getMaximumBinaryCompatibility() >= Compiler.JAVA_11) return;

		var prev = (InvokeHook)mn.getAttribute(InvokeHook.NAME);
		if (prev != null) {
			LavaCompiler.debugLogger().warn("Method {} Already have Evaluable!!!", mn);
		}

		var fwr = new MethodBridge((CompileUnit) ctx.compiler.resolve(mn.owner()), mn, prev);
		mn.addAttribute(fwr);
	}
}