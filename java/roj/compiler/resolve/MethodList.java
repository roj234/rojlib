package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.IntMap;
import roj.collect.MatchMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/28 0028 5:41
 */
final class MethodList extends ComponentList {
	IClass owner;
	final SimpleList<MethodNode> methods = new SimpleList<>(4);
	private int childId;
	private boolean hasVarargs, hasDefault;
	private MyHashSet<String> ddtmp = new MyHashSet<>();

	private volatile MatchMap<String, Object> namedLookup;

	void add(IClass klass, MethodNode mn) {
		// 忽略改变返回类型的重载的parent
		if (!ddtmp.add(TypeHelper.getMethod(mn.parameters()))) return;
		methods.add(mn);
		if ((mn.modifier & Opcodes.ACC_VARARGS) != 0) hasVarargs = true;
		mn.parsedAttr(klass.cp(), Attribute.SIGNATURE);
	}

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
	public MethodResult findMethod(LocalContext ctx, IType genericHint, List<IType> params,
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
			IClass mnOwner = ctx.classes.getClassInfo(mn.owner);

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

			MethodResult result = ctx.inferrer.infer(mnOwner, mn, genericHint, myParam == null ? params : myParam);
			int score = result.distance;
			if (score < 0) continue;

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
		if (!dup.isEmpty()) {
			CharList sb = new CharList().append("invoke.compatible.plural:").append(name).append(':');

			appendInput(params, sb);

			sb.append("  ").append(best.method.owner).append("{invoke.method}").append(name).append('(');
			getArg(best.method, sb).append(')');

			for (MethodNode mn : dup) {
				sb.append(" {and}\n  ").append(mn.owner).append("{invoke.method}").append(name).append('(');
				getArg(mn, sb).append(')');
			}

			ctx.report(Kind.ERROR, sb.replace('/', '.').append(" {invoke.matches}").toStringAndFree());
		} else if (best == null) {
			CharList sb = new CharList().append("invoke.incompatible.plural:").append(name).append('(');

			if (params.isEmpty()) sb.append("{invoke.no_param}");
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
				sb.append("  {invoke.method}").append(mn.owner).append('.').append(mn.name());
				getArg(mn, sb.append('(')).append("){invoke.notApplicable}\n    ");

				getErrorMsg(ctx, genericHint, params, (flags&IN_STATIC) != 0, mn, sb.append('('), tmp);
				sb.append(')').append('\n');
			}

			ctx.errorCapture = null;
			ctx.report(Kind.ERROR, sb.replace('/', '.').toStringAndFree());
		}

		return best;
	}

	private static void getErrorMsg(LocalContext ctx, IType genericHint, List<IType> params, boolean in_static, MethodNode mn, CharList sb, CharList errRpt) {
		IClass info = ctx.classes.getClassInfo(mn.owner);
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

		lookup.compat();
		namedLookup = lookup;
	}

	static void appendError(MethodResult mr, CharList sb) {
		sb.append(JavaLexer.translate.translate(mr.error == null || mr.error.length == 0 ? "typeCast.error."+mr.distance : "typeCast.error."+mr.distance+":"+mr.error[0]+":"+mr.error[1]));
	}
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

	@Override
	public String toString() {
		CharList sb = new CharList().append('[');

		for (MethodNode node : methods) {
			sb.append(node.ownerClass()).append(" => (").append(TextUtil.join(node.parameters(), ", ")).append(") => ").append(node.returnType())
			  .append(", ");
		}

		return sb.append(']').toStringAndFree();
	}
}