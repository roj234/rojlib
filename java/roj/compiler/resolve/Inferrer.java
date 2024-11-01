package roj.compiler.resolve;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.*;
import roj.collect.MyHashMap;
import roj.compiler.asm.Asterisk;
import roj.compiler.ast.expr.Lambda;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.util.ArrayCache;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;

/**
 * 参数推断和判断
 * @author Roj234
 * @since 2024/1/28 11:59
 */
public final class Inferrer {
	public static final MethodResult
		FAIL_ARGCOUNT = new MethodResult(TypeCast.E_GEN_PARAM_COUNT, ArrayCache.OBJECTS),
		FAIL_GENERIC = new MethodResult(TypeCast.E_GEN_PARAM_COUNT, ArrayCache.OBJECTS);

	private static final int LEVEL_DEPTH = 5120;

	private final LocalContext ctx;
	private final TypeCast castChecker = new TypeCast();

	// 类型参数的当前类型
	private final Map<String, IType> typeParams = new MyHashMap<>();
	// 类型参数的上界
	private final Map<String, List<IType>> typeParamBounds = new MyHashMap<>();

	@Nullable
	private Signature sign;
	private IType[] bounds;

	public Inferrer(LocalContext ctx) {
		this.ctx = ctx;
		this.castChecker.typeParamsL = typeParamBounds;
	}

	public MethodResult getGenericParameters(ConstantData info, MethodNode method, IType instanceType) {
		int argc = method.parameters().size();
		return infer(info, method, instanceType instanceof Generic g ? g : null, new AbstractList<>() {
			public IType get(int index) {return Asterisk.anyType;}
			public int size() {return argc;}
		});
	}

	// 泛型附加上界, 可以用来缩小上界的范围，但是没啥用
	public List<IType> manualTPBounds;
	// 覆盖模式, 推断方法覆盖时使用
	public boolean overrideMode;

	/**
	 * <a href="https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12">Method Invocation Expressions</a>
	 * 里面三步现在用magic number (distance)表示
	 * 如果性能不够再优化吧
	 */
	@SuppressWarnings("fallthrough")
	public MethodResult infer(ConstantData owner, MethodNode mn, IType instanceType, List<IType> input) {
		this.castChecker.context = ctx.classes;

		List<? extends IType> mpar;
		int mnSize;

		sign = mn.parsedAttr(owner.cp, Attribute.SIGNATURE);
		if (sign != null) {
			typeParamBounds.clear();
			typeParams.clear();

			int len = sign.values.size()-1;
			if (bounds == null || bounds.length < len) bounds = new IType[len];

			Map<String, List<IType>> myTP = sign.typeParams;
			typeParamBounds.putAll(myTP);

			if (manualTPBounds != null) {
				if (manualTPBounds.size() != myTP.size()) return FAIL_ARGCOUNT;

				int i = 0;
				for (String name : myTP.keySet())
					typeParams.put(name, manualTPBounds.get(i++));
			}

			Signature classSign = owner.parsedAttr(owner.cp, Attribute.SIGNATURE);
			block:
			if (classSign != null) {
				for (Map.Entry<String, List<IType>> entry : classSign.typeParams.entrySet()) {
					typeParamBounds.putIfAbsent(entry.getKey(), entry.getValue());
				}

				if (instanceType == null) break block;

				if (!instanceType.owner().equals(owner.name)) {
					List<IType> myBounds = ctx.inferGeneric(instanceType, owner.name);

					Generic g = new Generic(owner.name, (byte) 0);
					g.children = myBounds;
					instanceType = g;
				} else if (instanceType.genericType() == IType.CONCRETE_ASTERISK_TYPE) {
					// 解决了问题，但是它*必须*放在这里么
					instanceType = ((Asterisk) instanceType).getBound();
				}

				boundPreCheck:
				if (instanceType instanceof Generic gHint) {
					// TODO 非静态 泛型 内部类
					assert gHint.sub == null : "dynamic subclass not supported at this time";
					if (gHint.children.size() != typeParamBounds.size()) {
						if (gHint.children.size() == 1 && gHint.children.get(0) == Asterisk.anyGeneric)
							break boundPreCheck;

						return FAIL_GENERIC;
					}

					int i = 0;
					for (String name : classSign.typeParams.keySet()) {
						if (!myTP.containsKey(name)) typeParams.put(name, gHint.children.get(i));

						i++;
					}
				}
			}

			mpar = sign.values;
			mnSize = mpar.size()-1;
		} else {
			if (manualTPBounds != null) return FAIL_ARGCOUNT;

			mpar = mn.parameters();
			mnSize = mpar.size();
		}

		int distance = 0;
		int inSize = input.size();
		boolean dvc = false;

		checkSpecial:
		if ((mn.modifier&Opcodes.ACC_VARARGS) != 0 && !overrideMode) {
			int vararg = mnSize-1;
			if (inSize < vararg) return FAIL_ARGCOUNT;

			// varargs是最后一步
			distance += LEVEL_DEPTH*2;

			IType type = mpar.get(vararg);
			if (type.array() == 0) throw ResolveException.ofIllegalInput("inferrer.illegalVararg", mn.owner, mn.name());
			IType componentType = TypeHelper.componentType(type);

			if (inSize == vararg) {
				if (vararg == 0)
					distance -= cast(componentType, LocalContext.OBJECT_TYPE).distance;
				break checkSpecial;
			}

			int i = vararg;
			IType bound = input.get(i);
			TypeCast.Cast cast;
			block: {
				if (i+1 == inSize) {
					cast = cast(bound, type);
					if (cast.type >= 0) {
						if (type.array() == 1 && type.owner().equals("java/lang/Object")) {
							assert type.equals(bound);
						}
						dvc = true;
						break block;
					}
				}
				cast = cast(bound, componentType);
				if (cast.type < 0) return FAIL(i, bound, componentType, cast);
			}
			distance += cast.distance;
			i++;

			for (;i < inSize; i++) {
				IType from = input.get(i);
				cast = cast(from, bound);
				if (cast.type < 0) {
					bound = ctx.getCommonParent(from, bound);

					cast = cast(bound, componentType);
					if (cast.type < 0) return FAIL(i, bound, componentType, cast);
				}

				distance += cast.distance;
			}

			if (sign != null) bounds[vararg] = bound;
			inSize = vararg;
		} else if (mnSize != inSize) return FAIL_ARGCOUNT;

		boolean hasBox = false;
		for (int i = 0; i < inSize; i++) {
			IType from = input.get(i), to = mpar.get(i);

			var cast = overrideMode ? overrideCast(from, to) : cast(from, to);
			switch (cast.type) {
				default: return FAIL(i, from, to, cast);

				// 装箱/拆箱 第二步
				case TypeCast.UNBOXING: case TypeCast.BOXING: hasBox = true;
				case TypeCast.NUMBER_UPCAST: case TypeCast.UPCAST: distance += cast.distance; break;
			}

			if (sign != null) bounds[i] = from;
		}

		if (hasBox) distance += LEVEL_DEPTH;

		return addGeneric(new MethodResult(mn, distance, dvc));
	}
	// TODO 暂时能跑，不过能cover 100%么
	public TypeCast.Cast overrideCast(IType from, IType to) {
		if (to instanceof TypeParam n) to = typeParams.get(n.name);
		if (from.equals(to)) return TypeCast.RESULT(0, 0);

		if (from.genericType() <= IType.GENERIC_TYPE) {
			if (!from.isPrimitive() && !to.isPrimitive()) {
				// <?> or AnyType
				if (to.genericType() == IType.ANY_TYPE) {
					if (from.owner().equals("java/lang/Object") && from.array() == 0) {
						return TypeCast.RESULT(0, 0);
					}
				} else if (to.genericType() == IType.ASTERISK_TYPE) {
					return TypeCast.RESULT(0, 0);
				} else if (from.owner().equals(to.owner())) {
					// Generic cast check
					return castChecker.checkCast(from, to);
				}
			}
		} else {
			GlobalContext.debugLogger().warn("Unknown from type: "+from);
		}

		return TypeCast.ERROR(TypeCast.E_NEVER);
	}
	private TypeCast.Cast cast(IType from, IType to) {
		int lambdaArgCount = Lambda.getLambdaArgCount(from);
		if (lambdaArgCount >= 0) {
			if (!to.isPrimitive()) {
				var mn = ctx.classes.getResolveHelper(ctx.getClassOrArray(to)).getLambdaMethod();
				if (mn != null) {
					if (mn.parameters().size() == lambdaArgCount)
						return TypeCast.RESULT(TypeCast.UPCAST, 0);
				}
			}
			return TypeCast.RESULT(-99/*无效的函数接口; 仅定义在i18n中*/, lambdaArgCount);
		}

		return castChecker.checkCast(from, to);
	}
	private static MethodResult FAIL(int pos, IType from, IType to, TypeCast.Cast cast) { return new MethodResult(cast.type, from, to, pos); }

	private MethodResult addGeneric(MethodResult r) {
		if (sign == null) return r;

		for (int i = 0; i < sign.values.size()-1; i++) {
			if (bounds[i] == null) continue;
			try {
				mergeBound(bounds[i], sign.values.get(i));
			} catch (UnableCastException e) {
				return FAIL(i,e.from,e.to,e.code);
			}
			bounds[i] = null;
		}
		fillDefaultTypeParam(typeParamBounds, typeParams);

		if (!sign.values.isEmpty()) {
			r.desc = new IType[sign.values.size()];
			List<IType> values = sign.values;
			for (int i = 0; i < values.size(); i++) {
				r.desc[i] = cloneTP(values.get(i));
			}
		}

		if (sign.Throws != null) {
			r.exception = new IType[sign.Throws.size()];
			List<IType> values = sign.Throws;
			for (int i = 0; i < values.size(); i++) {
				r.exception[i] = cloneTP(values.get(i));
			}
		}

		return r;
	}
	private IType cloneTP(IType tt) { return clearTypeParam(tt, typeParams, typeParamBounds); }

	@SuppressWarnings("fallthrough")
	public static boolean hasUndefined(IType type) {
		switch (type.genericType()) {
			case IType.TYPE_PARAMETER_TYPE: return ((TypeParam) type).extendType != 0;
			case IType.ANY_TYPE: return true;
			case IType.GENERIC_TYPE: if (((Generic) type).extendType != 0) return true;
			case IType.GENERIC_SUBCLASS_TYPE:
				IGeneric t = (IGeneric) type;
				for (int i = 0; i < t.children.size(); i++)
					if (hasUndefined(t.children.get(i)))
						return true;
				if (t.sub != null && hasTypeParam(t.sub))
					return true;
		}

		return false;
	}

	public static boolean hasTypeParam(IType type) {
		switch (type.genericType()) {
			case IType.TYPE_PARAMETER_TYPE: return true;

			case IType.GENERIC_TYPE, IType.GENERIC_SUBCLASS_TYPE:
				IGeneric t = (IGeneric) type;
				for (int i = 0; i < t.children.size(); i++)
					if (hasTypeParam(t.children.get(i)))
						return true;
				if (t.sub != null && hasTypeParam(t.sub))
					return true;
		}

		return false;
	}
	public static void fillDefaultTypeParam(Map<String, List<IType>> bounds, Map<String, IType> realType) {
		for (Map.Entry<String, List<IType>> entry : bounds.entrySet()) {
			if (!realType.containsKey(entry.getKey())) {
				var value = entry.getValue();

				// 这里返回any的反向 - nullType => [? extends T] 可以upcast转换为任意继承T的对象，而不是downcast
				realType.put(entry.getKey(), new Asterisk(value.get(0).genericType() == IType.PLACEHOLDER_TYPE ? value.subList(1, value.size()) : value));
			}
		}
	}
	public static final ThreadLocal<Boolean> TEMPORARY_DISABLE_ASTERISK = new ThreadLocal<>();
	/**
	 * 按需复制 请勿再调用.clone()
	 */
	public static IType clearTypeParam(IType type, Map<String, IType> realType, Map<String, List<IType>> bounds) {
		switch (type.genericType()) {
			case IType.TYPE_PARAMETER_TYPE -> {
				TypeParam tt = (TypeParam) type;

				var exact = realType.get(tt.name);
				if (exact == null) throw new IllegalArgumentException("missing type param "+tt);

				if (exact.genericType() == IType.ASTERISK_TYPE) return exact;
				if (exact.genericType() == IType.ANY_TYPE) return LocalContext.OBJECT_TYPE;//Asterisk.anyType;

				if (tt.array() > 0) {
					exact = exact.clone();
					exact.setArrayDim(exact.array()+tt.array());
				}

				var bound = bounds.get(tt.name);
				if (tt.extendType == Generic.EX_SUPER) {
					GlobalContext.debugLogger().warn("EX_SUPER how to deal? typeParam={}, realType={}", tt, exact);
					return exact;
				}

				// TODO temporary workaround for SerializerFactory
				if (TEMPORARY_DISABLE_ASTERISK.get() != null) return exact;
				return new Asterisk(exact, bound.get(bound.get(0).genericType() == IType.PLACEHOLDER_TYPE ? 1 : 0));
			}
			case IType.ASTERISK_TYPE -> {
				Asterisk t = (Asterisk) type;
				List<IType> bounds1 = t.getBounds();
				for (int i = 0; i < bounds1.size(); i++) {
					IType prev = bounds1.get(i);
					IType elem = clearTypeParam(prev, realType, bounds);

					if (prev != elem) {
						if (type == t) {
							t = t.clone();
							bounds1 = t.getBounds();
						}

						bounds1.set(i, elem);
					}
				}

				return t;
			}
			case IType.GENERIC_TYPE, IType.GENERIC_SUBCLASS_TYPE -> {
				IGeneric t = (IGeneric) type;
				List<IType> children = t.children;
				for (int i = 0; i < children.size(); i++) {
					IType prev = children.get(i);
					IType elem = clearTypeParam(prev, realType, bounds);

					if (prev != elem) {
						if (type == t) {
							t = t.clone();
							children = t.children;
						}

						children.set(i, elem);
					}
				}

				if (t.sub != null) {
					var elem = clearTypeParam(t.sub, realType, bounds);
					if (t.sub != elem && type == t) {
						t = t.clone();
						t.sub = (GenericSub) elem;
					}
				}

				return t;
			}
		}

		return type;
	}

	private void mergeBound(IType paramSide, IType methodSide) {
		switch (methodSide.genericType()) {
			default: throw new UnsupportedOperationException("out="+paramSide+",in="+methodSide);
			case Type.STANDARD_TYPE, Type.PLACEHOLDER_TYPE, Type.ANY_TYPE: return;
			case Type.GENERIC_TYPE:
				// Asterisk
				if (!(paramSide instanceof Generic pSide)) return;

				Generic mSide = (Generic) methodSide;
				List<IType> children = pSide.children;
				if (children.size() != mSide.children.size()) throw new UnsupportedOperationException("GEN PARAM ERROR");
				for (int i = 0; i < children.size(); i++) {
					mergeBound(children.get(i), mSide.children.get(i));
				}
				if (mSide.sub != null) mergeBound(pSide.sub, mSide.sub);
			break;
			case Type.TYPE_PARAMETER_TYPE:
				TypeParam tp = (TypeParam) methodSide;
				//if (paramSide.isPrimitive())
				//	paramSide = TypeCast.getWrapper(paramSide.rawType());
				addBound(tp, paramSide);
			break;
		}
	}
	private void addBound(TypeParam tp, IType type) {
		IType bound = typeParams.get(tp.name);
		if (tp.extendType != 0) {
			type = new Generic(type.owner(), type.array()+tp.array(), tp.extendType);
		} else if (tp.array() != 0) {
			type = type.clone();
			type.setArrayDim(tp.array());
		}

		// equals是为了防止出现两个null type (但是可以出现两个asterisk type)
		bound = bound == null || type.equals(bound) ? type : getCommonChild(type, bound);
		typeParams.put(tp.name, bound);
	}
	private IType getCommonChild(IType a, IType b) {
		TypeCast.Cast cast = cast(a, b);
		if (cast.type >= 0) return b; // a更不具体
		cast = cast(b, a);
		if (cast.type >= 0) return a; // b更不具体

		throw new UnableCastException(a, b, cast);
	}
}