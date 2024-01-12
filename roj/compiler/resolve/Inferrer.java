package roj.compiler.resolve;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.*;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.compiler.asm.Asterisk;
import roj.compiler.context.CompileContext;
import roj.util.ArrayCache;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 参数推断和判断
 * @author Roj234
 * @since 2024/1/28 11:59
 */
public final class Inferrer {
	public static final MethodResult FAIL_ARGCOUNT = new MethodResult();
	static {
		FAIL_ARGCOUNT.distance = TypeCast.E_GEN_PARAM_COUNT;
		FAIL_ARGCOUNT.error = ArrayCache.OBJECTS;
	}
	private static final int LEVEL_DEPTH = 5120;

	private final CompileContext ctx;

	private final Map<String, List<IType>> typeParamBounds = new MyHashMap<>();
	private final Function<String, List<IType>> myGenericEnv = typeParamBounds::get;
	private final MyHashSet<String> pgUser = new MyHashSet<>();
	private final Map<String, IType> typeParams = new MyHashMap<>();

	@Nullable
	private Signature sign;
	private IType[] bounds;

	public Inferrer(CompileContext ctx) { this.ctx = ctx; }

	/**
	 * <a href="https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12">Method Invocation Expressions</a>
	 * 里面三步现在用magic number (distance)表示
	 * 如果性能不够再优化吧
	 */
	@SuppressWarnings("fallthrough")
	public MethodResult infer(IClass owner, MethodNode mn, IType genericHint, List<IType> input) {
		List<? extends IType> mpar;
		int mnSize;

		sign = mn.parsedAttr(owner.cp(), Attribute.SIGNATURE);
		if (sign != null) {
			typeParamBounds.clear();
			typeParams.clear();
			pgUser.clear();

			int len = sign.values.size()-1;
			if (bounds == null || bounds.length < len) bounds = new IType[len];

			typeParamBounds.putAll(sign.typeParams);

			Signature classSign = owner.parsedAttr(owner.cp(), Attribute.SIGNATURE);
			if (classSign != null) {
				for (Map.Entry<String, List<IType>> entry : classSign.typeParams.entrySet()) {
					typeParamBounds.putIfAbsent(entry.getKey(), entry.getValue());
				}

				boundPreCheck:
				if (genericHint instanceof Generic gHint) {
					// TODO 非静态 泛型 内部类
					assert gHint.sub == null : "dynamic subclass not supported at this time";
					if (gHint.children.size() != len) {
						if (gHint.children.size() ==1 && gHint.children.get(0) == Asterisk.anyGeneric)
							break boundPreCheck;

						throw new ResolveException("GENERIC ARG COUNT ERROR");
					}

					Map<String, List<IType>> map = sign.typeParams;

					int i = 0;
					for (Map.Entry<String, List<IType>> entry : classSign.typeParams.entrySet()) {
						if (!map.containsKey(entry.getKey())) typeParams.putIfAbsent(entry.getKey(), gHint.children.get(i));

						i++;
					}
				}
			}

			mpar = sign.values;
			mnSize = mpar.size()-1;
		} else {
			mpar = mn.parameters();
			mnSize = mpar.size();
		}

		int distance = 0;
		int inSize = input.size();
		boolean dvc = false;

		checkSpecial:
		if ((mn.modifier&Opcodes.ACC_VARARGS) != 0) {
			int vararg = mnSize-1;
			if (inSize < vararg) return FAIL_ARGCOUNT;

			// varargs是最后一步
			distance += LEVEL_DEPTH*2;

			IType type = mpar.get(vararg);
			if (type.array() == 0) throw new ResolveException("varargs方法"+mn.owner+"."+mn.name()+"未以数组作为最后一个参数");
			IType componentType = TypeHelper.componentType(type);

			if (inSize == vararg) {
				if (vararg == 0)
					distance -= cast(componentType, CompileContext.OBJECT_TYPE).distance;
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

			TypeCast.Cast cast = cast(from, to);
			switch (cast.type) {
				default: return FAIL(i, from, to, cast);

				// 装箱/拆箱 第二步
				case TypeCast.UNBOXING: case TypeCast.BOXING: hasBox = true;
				case TypeCast.NUMBER_UPCAST: case TypeCast.UPCAST: distance += cast.distance; break;
			}

			if (sign != null) bounds[i] = from;
		}

		if (hasBox) distance += LEVEL_DEPTH;

		MethodResult r = new MethodResult();
		r.method = mn;
		r.distance = distance;
		r.directVarargCall = dvc;
		return addGeneric(r);
	}
	private TypeCast.Cast cast(IType from, IType to) { return TypeCast.checkCast(from, to, ctx.GlobalClassEnv(), myGenericEnv); }
	private static MethodResult FAIL(int pos, IType from, IType to, TypeCast.Cast cast) {
		MethodResult r = new MethodResult();
		r.distance = cast.type;
		r.error = new Object[] { from, to, pos };
		return r;
	}

	private MethodResult addGeneric(MethodResult r) {
		if (sign == null) return r;

		for (int i = 0; i < sign.values.size()-1; i++) {
			try {
				mergeBound(bounds[i], sign.values.get(i));
			} catch (UnableCastException e) {
				return FAIL(i,e.from,e.to,e.code);
			}
			bounds[i] = null;
		}
		for (Map.Entry<String, List<IType>> entry : typeParamBounds.entrySet()) {
			List<IType> value = entry.getValue();
			if (!typeParams.containsKey(entry.getKey())) {
				// 这里返回any的反向 - nullType => [? extends T] 可以upcast转换为任意继承T的对象，而不是downcast
				typeParams.put(entry.getKey(), new Asterisk(value.get(0).genericType() == IType.PLACEHOLDER_TYPE ? value.subList(1, value.size()) : value));
			}
		}

		if (!sign.values.isEmpty()) {
			r.desc = new IType[sign.values.size()];
			List<IType> values = sign.values;
			for (int i = 0; i < values.size(); i++) {
				r.desc[i] = values.get(i).clone().resolveTypeParam(typeParams, typeParamBounds);
			}
		}

		if (sign.Throws != null) {
			r.exception = new IType[sign.Throws.size()];
			List<IType> values = sign.Throws;
			for (int i = 0; i < values.size(); i++) {
				IType value = values.get(i).clone();
				r.exception[i] = value.resolveTypeParam(typeParams, typeParamBounds);
			}
		}

		return r;
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
				if (paramSide.isPrimitive() && !pgUser.contains(tp.name))
					paramSide = TypeCast.getWrapper(paramSide.rawType());
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
		if (cast.type == TypeCast.UPCAST) return b; // a更不具体
		cast = cast(b, a);
		if (cast.type == TypeCast.UPCAST)  return a; // b更不具体

		throw new UnableCastException(a, b, cast);
	}
}