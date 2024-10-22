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
import roj.compiler.CompilerSpec;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.CompileUnit;
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
	final SimpleList<MethodNode> methods = new SimpleList<>();
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

			MethodResult result = ctx.inferrer.infer(mnOwner, mn, that, myParam == null ? params : myParam);
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

			sb.append("  ").append(best.method.owner).append("\1invoke.method\0").append(name).append('(');
			getArg(best.method, sb).append(')');

			for (MethodNode mn : dup) {
				sb.append(" \1and\0\n  ").append(mn.owner).append("\1invoke.method\0").append(name).append('(');
				getArg(mn, sb).append(')');
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
				sb.append("  \1invoke.method\0").append(mn.owner).append('.').append(mn.name());
				getArg(mn, sb.append('(')).append(")\1invoke.notApplicable\0\n    ");

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

		lookup.compat();
		namedLookup = lookup;
	}

	static void appendError(MethodResult mr, CharList sb) {
		sb.append("\1typeCast.error.").append(mr.distance);
		if (mr.error != null && mr.error.length == 3)
			sb.append(':').append(mr.error[0]).append(':').append(mr.error[1]);
		sb.append('\0');
	}
	static void appendInput(List<IType> params, CharList sb) {
		sb.append("  ").append("\1invoke.found\0 ");
		if (params.isEmpty()) sb.append("\1invoke.no_param\0");
		else sb.append(TextUtil.join(params, ","));
		sb.append('\n');
	}
	static CharList getArg(MethodNode mn, CharList sb) {
		Signature sign = mn.parsedAttr(null, Attribute.SIGNATURE);
		List<? extends IType> params2 = sign == null ? mn.parameters() : sign.values.subList(0, sign.values.size()-1);
		return sb.append(params2.isEmpty() ? "\1invoke.no_param\0" : TextUtil.join(params2, ","));
	}

	@Override
	public List<MethodNode> getMethods() {return methods;}

	@Override
	public String toString() {
		CharList sb = new CharList().append('[');

		for (MethodNode node : methods) {
			sb.append(node.ownerClass()).append(" => (").append(TextUtil.join(node.parameters(), ", ")).append(") => ").append(node.returnType())
			  .append(", ");
		}

		return sb.append(']').toStringAndFree();
	}

	static void checkBridgeMethod(LocalContext ctx, MethodResult mr) {
		var mn = mr.method;
		if ((mn.modifier&Opcodes.ACC_PRIVATE) == 0 || ctx.file.name.equals(mn.owner) ||
			ctx.classes.isSpecEnabled(CompilerSpec.NESTED_MEMBER)) return;

		var prev = (Evaluable)mn.attrByName(Evaluable.NAME);
		if (prev != null) {
			GlobalContext.debugLogger().warn("Method {} Already have Evaluable!!!", mn);
		}

		var fwr = new MethodBridge((CompileUnit) ctx.classes.getClassInfo(mn.owner), mn, prev);
		mn.putAttr(fwr);
	}
}