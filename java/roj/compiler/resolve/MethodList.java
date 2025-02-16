package roj.compiler.resolve;

import roj.asm.IClass;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.util.ClassUtil;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.*;
import roj.compiler.LavaFeatures;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
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
 * @since 2024/1/28 0028 5:41
 */
final class MethodList extends ComponentList {
	IClass owner;
	final SimpleList<MethodNode> methods = new SimpleList<>();
	private int childId;
	private boolean hasVarargs, hasDefault;
	private MyHashMap<String, List<MethodNode>> ddtmp = new MyHashMap<>();
	private MyBitSet overrider;

	private volatile MatchMap<String, Object> namedLookup;

	void add(IClass klass, MethodNode mn) {
		// 忽略改变返回类型的重载的parent
		var list = ddtmp.computeIfAbsent(Type.toMethodDesc(mn.parameters()), Helpers.fnArrayList());
		for (int i = 0; i < list.size(); i++) {
			var prev = list.get(i);
			if (ClassUtil.isOverridable(klass.name(), mn.modifier, prev.owner)) {
				if (overrider == null) overrider = new MyBitSet();
				overrider.add(methods.indexOfAddress(prev));
				return;
			}
		}
		list.add(mn);

		methods.add(mn);
		if ((mn.modifier & Opcodes.ACC_VARARGS) != 0) hasVarargs = true;
		mn.parsedAttr(klass.cp(), Attribute.SIGNATURE);
	}

	/**
	 * @param klass 所有者
	 * @return 是否可以压缩为MethodListSingle
	 */
	boolean pack(IClass klass) {
		ddtmp = null;
		owner = klass;

		String owner = klass.name();
		for (int i = 0; i < methods.size(); i++) {
			if (!owner.equals(methods.get(i).owner)) {
				childId = i;
				return false;
			}
		}
		childId = methods.size();
		return childId == 1;
	}

	@Override public boolean isOverriddenMethod(int id) {return overrider != null && overrider.contains(id);}
	@Override public List<MethodNode> getMethods() {return methods;}

	private static final class Filter extends SimpleList<Object> {
		@Override
		public boolean add(Object entry) {
			if (entry instanceof MatchMap.AbstractEntry<?> entry1) {
				if (entry1.value instanceof List<?>) {
					for (Object o : (Iterable<?>) entry1.value)
						super.add(o);
				} else {
					super.add(entry1.value);
				}
				return true;
			}
			return super.add(entry);
		}
	}
	public MethodResult findMethod(LocalContext ctx, IType that, List<IType> params,
								   Map<String, IType> namedType, int flags) {
		SimpleList<MethodNode> candidates;

		// 还是别过早优化了
		block: {
			/*if (methods.size() > 5) {
				createLookup();

				CharList tmp = new CharList();
				tmp.append((char) 127);
				for (int j = 0; j < params.size(); j++) {
					IType type = params.get(j);
					if (type == NullType.nulltype) tmp.append('O');
					else paramType(type, tmp);
					tmp.append((char) (j + 128));
				}

				int flag = 0;
				if (hasVarargs) flag |= MatchMapString.MATCH_SHORTER;
				if (hasDefault) flag |= MatchMapString.MATCH_LONGER;

				candidates = Helpers.cast(new Filter());
				unnamedLookup.matchOrdered(tmp.toString(), flag, Helpers.cast(candidates));
				break block;
			}*/

			candidates = methods;
		}

		MethodResult best = null;
		List<MethodNode> dup = new SimpleList<>();
		SimpleList<IType> myParam = null;

		int size = (flags&THIS_ONLY) != 0 ? childId : candidates.size();

		loop:
		for (int j = 0; j < size; j++) {
			MethodNode mn = candidates.get(j);
			var mnOwner = ctx.classes.getClassInfo(mn.owner);

			if (!ctx.checkAccessible(mnOwner, mn, (flags&IN_STATIC) != 0, false)) continue;

			List<Type> mParam = mn.parameters();
			IntMap<Object> defParamState = null;

			varargCheck:
			if (mParam.size() != params.size()) {
				if ((mn.modifier & Opcodes.ACC_VARARGS) != 0) break varargCheck;

				int defReq = mParam.size() - params.size() - namedType.size();
				if(defReq < 0) continue;

				IntMap<ExprNode> mdvalue = ctx.getDefaultValue(mnOwner, mn);
				if (defReq > mdvalue.size()) continue;

				defParamState = new IntMap<>();
				if (myParam == null) myParam = new SimpleList<>(params);
				else myParam._setSize(params.size());

				List<String> names = ParamNameMapper.getParameterName(mnOwner.cp(), mn);

				if (names == null) {
					for (int i = params.size(); i < mParam.size(); i++) {
						ExprNode c = mdvalue.get(i);
						if (c == null) continue loop;
						defParamState.putInt(i, c);
						myParam.add(c.type());
					}
				} else if (names.size() != mParam.size()) {
					ctx.report(Kind.WARNING, "invoke.warn.illegalNameList", mn);
					continue;
				} else {
					for (int i = params.size(); i < mParam.size(); i++) {
						String name = names.get(i);
						IType type = namedType.get(name);
						if (type == null) {
							ExprNode c = mdvalue.get(i);
							if (c == null) continue loop;
							type = c.type();
							defParamState.putInt(i, c);
						} else {
							defParamState.putInt(i, name);
						}
						myParam.add(type);
					}
				}
			}

			var result = ctx.inferrer.infer(mnOwner, mn, that, myParam == null ? params : myParam);
			if (result.method == null) continue;

			int score = result.distance;
			if (best == null || score <= best.distance) {
				if (best != null && score == best.distance) dup.add(mn);
				else {
					dup.clear();
					best = result;
					best.namedParams = defParamState;
				}
			}
		}

		String name = methods.get(0).name();
		boolean isConstructor = name.equals("<init>");
		if (isConstructor) name = owner.name().substring(owner.name().lastIndexOf('/')+1);

		if (!dup.isEmpty()) {
			CharList sb = new CharList().append("invoke.compatible.plural:").append(name).append(':');

			appendInput(params, sb);

			sb.append("  ").append(best.method.owner).append("\1invoke.method\0").append(name).append('(');
			getArg(best.method, that, sb).append(')');

			for (MethodNode mn : dup) {
				sb.append(" \1and\0\n  ").append(mn.owner).append("\1invoke.method\0").append(name).append('(');
				getArg(mn, that, sb).append(')');
			}

			ctx.report(Kind.ERROR, sb.replace('/', '.').append(" \1invoke.matches\0").toStringAndFree());
		} else if (best == null) {
			if ((flags & NO_REPORT) != 0) return null;

			CharList sb = new CharList().append("invoke.incompatible.plural:").append(name).append('(');

			if (params.isEmpty()) sb.append("\1invoke.no_param\0");
			else sb.append(TextUtil.join(params, ","));
			sb.append("):");

			CharList tmp = new CharList();
			ctx.errorCapture = (trans, param) -> {
				tmp.clear();
				tmp.append(trans);
				for (Object o : param)
					tmp.append(':').append(o);
			};

			for (int i = 0; i < size; i++) {
				MethodNode mn = methods.get(i);
				if (isConstructor) sb.append("  \1invoke.constructor\0").append(mn.owner);
				else sb.append("  \1invoke.method\0").append(mn.owner).append('.').append(mn.name());
				getArg(mn, that, sb.append('(')).append(")\1invoke.notApplicable\0\n    ");

				getErrorMsg(ctx, that, params, (flags&IN_STATIC) != 0, mn, sb.append("(\1"), tmp);
				sb.append("\0)\n");
			}

			ctx.errorCapture = null;
			ctx.report(Kind.ERROR, sb.replace('/', '.').toStringAndFree());
		} else {
			checkBridgeMethod(ctx, best);
		}

		return best;
	}

	private static void getErrorMsg(LocalContext ctx, IType genericHint, List<IType> params, boolean in_static, MethodNode mn, CharList sb, CharList errRpt) {
		var info = ctx.classes.getClassInfo(mn.owner);
		if (!ctx.checkAccessible(info, mn, in_static, true)) {
			sb.append(errRpt);
		} else {
			appendError(ctx.inferrer.infer(info, mn, genericHint, params), sb);
		}
	}

	@SuppressWarnings("unchecked")
	private void createNamedLookup(GlobalContext ctx) {
		if (namedLookup != null) return;

		MatchMap<String, Object> lookup = new MatchMap<>();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			List<String> name1 = ParamNameMapper.getParameterName(ctx.getClassInfo(mn.owner).cp(), mn);
			if (name1 == null) continue;

			name1.sort(null);
			name1.add("\0");

			MatchMap.Entry<Object> entry = lookup.getEntry(name1);
			if (entry == null) lookup.add(name1, mn);
			else if (entry.value instanceof List) ((List<Object>) entry.value).add(mn);
			else entry.value = SimpleList.asModifiableList(entry.value, mn);
		}

		lookup.compact();
		namedLookup = lookup;
	}

	static void appendError(MethodResult mr, CharList sb) {
		sb.append("typeCast.error.").append(mr.distance);
		if (mr.error != null && mr.error.length == 3)
			sb.append(':').append(mr.error[0]).append(':').append(mr.error[1]);
	}
	static void appendInput(List<IType> params, CharList sb) {
		sb.append("  ").append("\1invoke.found\0 ");
		if (params.isEmpty()) sb.append("\1invoke.no_param\0");
		else sb.append(TextUtil.join(params, ","));
		sb.append('\n');
	}
	static CharList getArg(MethodNode mn, IType that, CharList sb) {
		Signature sign = mn.parsedAttr(null, Attribute.SIGNATURE);
		List<? extends IType> params = sign == null ? mn.parameters() : sign.values.subList(0, sign.values.size()-1);
		if (params.isEmpty()) return sb.append("\1invoke.no_param\0");

		var myList = that instanceof Generic g ? g.children : Collections.emptyList();

		int i = 0;
		while (true) {
			sb.append(params.get(i));
			if (i < myList.size()) {
				sb.append("\1invoke.generic.s\0").append(myList.get(i)).append("\1invoke.generic.e\0");
			}
			if (++i == params.size()) break;
			sb.append(",");
		}

		return sb;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append('[');

		for (MethodNode node : methods) {
			sb.append(node.returnType()).append(' ').append(node.owner).append('.').append(node.name()).append('(').append(TextUtil.join(node.parameters(), ", ")).append("), ");
		}

		return sb.append(']').toStringAndFree();
	}

	static void checkBridgeMethod(LocalContext ctx, MethodResult mr) {
		var mn = mr.method;
		if ((mn.modifier&Opcodes.ACC_PRIVATE) == 0 || ctx.file.name().equals(mn.owner) ||
			ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER)) return;

		var prev = (Evaluable)mn.attrByName(Evaluable.NAME);
		if (prev != null) {
			GlobalContext.debugLogger().warn("Method {} Already have Evaluable!!!", mn);
		}

		var fwr = new MethodBridge((CompileUnit) ctx.classes.getClassInfo(mn.owner), mn, prev);
		mn.putAttr(fwr);
	}
}