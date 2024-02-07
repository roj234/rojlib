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

/**
 * 参数推断和判断
 * @author Roj234
 * @since 2024/1/28 11:59
 */
public final class Inferrer {
	public static final MethodResult FAIL_ARGCOUNT = new MethodResult(TypeCast.E_GEN_PARAM_COUNT, ArrayCache.OBJECTS);
	private static final int LEVEL_DEPTH = 5120;

	private final CompileContext ctx;
	private final TypeCast castChecker = new TypeCast();

	private final Map<String, IType> typeParams = new MyHashMap<>();
	private final Map<String, List<IType>> typeParamBounds = new MyHashMap<>();
	private final MyHashSet<String> pgUser = new MyHashSet<>();

	@Nullable
	private Signature sign;
	private IType[] bounds;

	public Inferrer(CompileContext ctx) {
		this.ctx = ctx;
		this.castChecker.genericResolver = typeParamBounds::get;
	}

	public List<IType> typeParamHint;

	/**
	 * <a href="https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12">Method Invocation Expressions</a>
	 * 里面三步现在用magic number (distance)表示
	 * 如果性能不够再优化吧
	 */
	@SuppressWarnings("fallthrough")
	public MethodResult infer(IClass owner, MethodNode mn, IType typeMirror, List<IType> input) {
		this.castChecker.context = ctx.classes;

		List<? extends IType> mpar;
		int mnSize;

		sign = mn.parsedAttr(owner.cp(), Attribute.SIGNATURE);
		if (sign != null) {
			typeParamBounds.clear();
			typeParams.clear();
			pgUser.clear();

			int len = sign.values.size()-1;
			if (bounds == null || bounds.length < len) bounds = new IType[len];

			Map<String, List<IType>> myTP = sign.typeParams;
			typeParamBounds.putAll(myTP);

			if (typeParamHint != null) {
				if (typeParamHint.size() != myTP.size()) return FAIL_ARGCOUNT;

				int i = 0;
				for (String name : myTP.keySet())
					typeParams.put(name, typeParamHint.get(i++));
			}

			Signature classSign = owner.parsedAttr(owner.cp(), Attribute.SIGNATURE);
			if (classSign != null) {
				for (Map.Entry<String, List<IType>> entry : classSign.typeParams.entrySet()) {
					typeParamBounds.putIfAbsent(entry.getKey(), entry.getValue());
				}

				boundPreCheck:
				if (typeMirror instanceof Generic gHint) {
					// TODO 非静态 泛型 内部类
					assert gHint.sub == null : "dynamic subclass not supported at this time";
					if (gHint.children.size() != len) {
						if (gHint.children.size() ==1 && gHint.children.get(0) == Asterisk.anyGeneric)
							break boundPreCheck;

						throw new ResolveException("GENERIC ARG COUNT ERROR");
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
			if (typeParamHint != null) return FAIL_ARGCOUNT;

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

		return addGeneric(new MethodResult(mn, distance, dvc));
	}
	private TypeCast.Cast cast(IType from, IType to) { return castChecker.checkCast(from, to); }
	private static MethodResult FAIL(int pos, IType from, IType to, TypeCast.Cast cast) { return new MethodResult(cast.type, from, to, pos); }

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
	private IType cloneTP(IType tt) { return hasTypeParam(tt) ? clearTypeParams(tt.clone(), typeParams, typeParamBounds) : tt; }

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
	public static IType clearTypeParams(IType type, Map<String, IType> visType, Map<String, List<IType>> bounds) {
		switch (type.genericType()) {
			case IType.TYPE_PARAMETER_TYPE:
				TypeParam tt = (TypeParam) type;
				IType alt = visType.get(tt.name);
				if (alt == null) throw new IllegalArgumentException("missing type param "+tt);
				// TODO check line below
				if (alt.genericType() == IType.ASTERISK_TYPE) return alt;

				List<IType> types = bounds.get(tt.name);
				return new Asterisk(tt.extendType == 0
					? new Type(alt.owner(), alt.array()+tt.array())
					: new Generic(alt.owner(), alt.array()+tt.array(), tt.extendType),
					types.get(types.get(0).genericType() == IType.PLACEHOLDER_TYPE ? 1 : 0));

			case IType.GENERIC_TYPE, IType.GENERIC_SUBCLASS_TYPE:
				IGeneric t = (IGeneric) type;
				for (int i = 0; i < t.children.size(); i++)
					t.children.set(i, clearTypeParams(t.children.get(i), visType, bounds));
				if (t.sub != null) clearTypeParams(t.sub, visType, bounds);
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