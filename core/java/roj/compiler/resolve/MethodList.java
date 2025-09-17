package roj.compiler.resolve;

import roj.asm.ClassDefinition;
import roj.asm.ClassUtil;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asmx.mapper.ParamNameMapper;
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
import roj.compiler.diagnostic.Kind;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/28 5:41
 */
final class MethodList extends ComponentList {
	ClassDefinition owner;
	final ArrayList<MethodNode> methods = new ArrayList<>();
	private int childId;
	private HashMap<String, List<MethodNode>> ddtmp = new HashMap<>();
	private BitSet overrider;

	void add(ClassDefinition klass, MethodNode mn) {
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
		mn.getAttribute(klass.cp(), Attribute.SIGNATURE);
	}

	/**
	 * @param klass 所有者
	 * @return 是否可以压缩为MethodListSingle
	 */
	boolean pack(ClassDefinition klass) {
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

			if (!ctx.checkAccessible(owner, method, (flags&IN_STATIC) != 0, false)) continue;

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

				List<String> paramNames = ParamNameMapper.getParameterNames(this.owner.cp(), method);
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

			var result = ctx.inferrer.infer(owner, method, that, myParam == null ? actualArguments : myParam);
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
			CharList sb = new CharList().append("invoke.compatible.plural:[\"").append(name).append("\",[");

			appendInput(actualArguments, sb);

			sb.append("\"  \",\"").append(best.method.owner()).append("\",invoke.method,\"").append(name).append("(\",");
			getArg(best.method, that, sb).append("\")\",");

			for (MethodNode mn : dup) {
				sb.append("\" \",and,\"\n  \",\"").append(mn.owner()).append("\",invoke.method,\"").append(name).append("(\",");
				getArg(mn, that, sb).append("\")\",");
			}

			ctx.report(Kind.ERROR, sb.replace('/', '.').append("\" \",invoke.matches]").toStringAndFree());
		} else if (best == null) {
			if ((flags & NO_REPORT) != 0) return null;

			CharList sb = new CharList().append("invoke.incompatible.plural:[[\"").append(name).append("(\",");

			if (actualArguments.isEmpty()) sb.append("invoke.no_param");
			else sb.append('"').append(TextUtil.join(actualArguments, ",")).append('"');
			sb.append(",\")\"],[");

			CharList tmp = new CharList();
			ctx.errorCapture = makeErrorCapture(tmp);

			for (int i = 0; i < size; i++) {
				MethodNode mn = methods.get(i);
				if (isConstructor) sb.append("\"  \",invoke.constructor,\"").append(mn.owner());
				else sb.append("\"  \",invoke.method,\"").append(mn.owner()).append('.').append(mn.name());
				getArg(mn, that, sb.append("\",\"(\",")).append("\")\",invoke.notApplicable,\"\n    \",");

				getErrorMsg(ctx, that, actualArguments, (flags&IN_STATIC) != 0, mn, sb.append("\"(\","), tmp);
				sb.append("\")\n\",");
			}
			sb.set(sb.length()-1, ']');

			ctx.errorCapture = null;
			ctx.report(Kind.ERROR, sb.replace('/', '.').toStringAndFree());
		} else {
			checkDeprecation(ctx, owner, best.method);
			checkBridgeMethod(ctx, best);
		}

		return best;
	}

	private static void getErrorMsg(CompileContext ctx, IType genericHint, List<IType> params, boolean in_static, MethodNode mn, CharList sb, CharList errRpt) {
		var info = ctx.compiler.resolve(mn.owner());
		if (ctx.checkAccessible(info, mn, in_static, true)) {
			appendError(ctx.inferrer.infer(info, mn, genericHint, params), sb);
		}
		if (errRpt.length() > 0) {
			sb.append(errRpt).append(',');
			errRpt.clear();
		}
	}

	static void appendError(MethodResult mr, CharList sb) {
		if (mr.distance > 0) return;
		sb.append("typeCast.error.").append(mr.distance);
		if (mr.error != null && mr.error.length == 3)
			sb.append(":[\"").append(mr.error[0]).append("\",\"").append(mr.error[1]).append("\"]");
		sb.append(',');
	}
	static void appendInput(List<IType> params, CharList sb) {
		sb.append("\"  \",invoke.found,\" \",");
		if (params.isEmpty()) sb.append("invoke.no_param");
		else sb.append('"').append(TextUtil.join(params, ",")).append('"');
		sb.append(",\"\n\",");
	}
	static CharList getArg(MethodNode mn, IType that, CharList sb) {
		Signature sign = mn.getAttribute(null, Attribute.SIGNATURE);
		List<? extends IType> params = sign == null ? mn.parameters() : sign.values.subList(0, sign.values.size()-1);
		if (params.isEmpty()) return sb.append("invoke.no_param,");

		var myList = that instanceof Generic g ? g.children : Collections.emptyList();
		int i = 0;
		while (true) {
			sb.append('"').append(params.get(i)).append("\",");
			if (i < myList.size()) sb.append(",invoke.generic.s,\"").append(myList.get(i)).append("\",invoke.generic.e,");
			if (++i == params.size()) break;
			sb.append("\",\",");
		}

		return sb;
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