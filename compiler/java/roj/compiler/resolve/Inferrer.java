package roj.compiler.resolve;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.*;
import roj.collect.HashMap;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Types;
import roj.compiler.asm.WildcardType;
import roj.compiler.ast.expr.Lambda;
import roj.util.ArrayCache;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 参数推断和判断
 * @author Roj234
 * @since 2024/1/28 11:59
 */
public final class Inferrer {
	public static final MethodResult
		FAIL_ARGCOUNT = new MethodResult(-97, ArrayCache.OBJECTS),
		FAIL_GENERIC = new MethodResult(TypeCast.GENERIC_PARAM_COUNT, ArrayCache.OBJECTS);

	private static final int LEVEL_DEPTH = 5120;

	private final CompileContext ctx;
	private final TypeCast castChecker = new TypeCast();

	// 类型参数的当前类型
	private final Map<String, IType> typeParams = new HashMap<>();
	// 类型参数的上界
	private final Map<String, List<IType>> typeParamBounds = new HashMap<>();

	@Nullable
	private Signature sign;
	private IType[] bounds;

	public Inferrer(CompileContext ctx) {
		this.ctx = ctx;
		this.castChecker.typeParams = typeParamBounds;
		this.castChecker.context = ctx.compiler;
	}

	public MethodResult getGenericParameters(ClassNode info, MethodNode method, @Nullable IType instanceType) {
		overrideMode = true;
		MethodResult mr = infer(info, method, instanceType instanceof ParameterizedType g ? g : null, Collections.emptyList());
		overrideMode = false;
		return mr;
	}

	// 泛型附加上界, 可以用来缩小上界的范围，但是没啥用
	public List<IType> manualTPBounds;
	// 覆盖模式, 推断方法覆盖时使用
	boolean overrideMode;
	// MethodListSingle的特殊处理，在推断阶段允许数字降级，到write阶段再根据常量值计算能否调用
	int _minCastAllow;

	/**
	 * <a href="https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12">Method Invocation Expressions</a>
	 * 里面三步现在用magic number (distance)表示
	 * 如果性能不够再优化吧
	 */
	@SuppressWarnings("fallthrough")
	public MethodResult infer(ClassNode owner, MethodNode mn, IType instanceType, List<IType> input) {
		this.castChecker.context = ctx.compiler;

		List<? extends IType> mpar;
		int mnSize;

		sign = mn.getAttribute(owner.cp, Attribute.SIGNATURE);
		if (sign != null) {
			typeParamBounds.clear();
			typeParams.clear();

			int len = sign.values.size()-1;
			if (bounds == null || bounds.length < len) bounds = new IType[len];

			Map<String, List<IType>> myTP = sign.typeVariables;
			typeParamBounds.putAll(myTP);

			if (manualTPBounds != null) {
				if (manualTPBounds.size() != myTP.size()) return FAIL_ARGCOUNT;

				int i = 0;
				for (String name : myTP.keySet())
					typeParams.put(name, manualTPBounds.get(i++));
			}

			Signature classSign = owner.getAttribute(owner.cp, Attribute.SIGNATURE);
			block:
			if (classSign != null) {
				for (Map.Entry<String, List<IType>> entry : classSign.typeVariables.entrySet()) {
					typeParamBounds.putIfAbsent(entry.getKey(), entry.getValue());
				}

				if (instanceType == null) break block;

				if (!instanceType.owner().equals(owner.name())) {
					List<IType> myBounds = ctx.inferGeneric(instanceType, owner.name());

					assert myBounds != null;
					ParameterizedType g = new ParameterizedType(owner.name(), ParameterizedType.NO_WILDCARD);
					g.typeParameters = myBounds;
					instanceType = g;
				} else if (instanceType.kind() == IType.CAPTURED_WILDCARD) {
					// 解决了问题，但是它*必须*放在这里么
					instanceType = ((WildcardType) instanceType).getBound();
				}

				boundPreCheck:
				if (instanceType instanceof ParameterizedType gHint) {
					// TODO 非静态 泛型 内部类
					assert gHint.sub == null : "dynamic subclass not supported at this time";
					if (gHint.typeParameters.size() != typeParamBounds.size()) {
						if (gHint.typeParameters.size() == 1 && gHint.typeParameters.get(0) == WildcardType.anyGeneric)
							break boundPreCheck;

						return FAIL_GENERIC;
					}

					int i = 0;
					for (String name : classSign.typeVariables.keySet()) {
						if (!myTP.containsKey(name)) typeParams.put(name, gHint.typeParameters.get(i));

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

		if (!overrideMode)
		checkSpecial:
		if ((mn.modifier&Opcodes.ACC_VARARGS) != 0) {
			int vararg = mnSize-1;
			if (inSize < vararg) return FAIL_ARGCOUNT;

			// varargs是最后一步
			distance += LEVEL_DEPTH*2;

			IType type = mpar.get(vararg);
			if (type.array() == 0) throw ResolveException.ofIllegalInput("semantic.resolution.illegalVararg", mn.owner(), mn.name());
			IType componentType = TypeHelper.componentType(type);

			if (inSize == vararg) {
				if (vararg == 0)
					distance -= cast(componentType, Types.OBJECT_TYPE).distance;
				break checkSpecial;
			}

			int i = vararg;
			IType bound = input.get(i);
			TypeCast.Cast cast = null;
			checkFirstArg: {
				if (i+1 == inSize) {
					cast = cast(bound, type);
					if (cast.type >= 0) {
						if (type.array() == 1 && "java/lang/Object".equals(type.owner())) {
							if (!type.equals(bound)) {
								// TODO Dangerous!
								ctx.report(roj.compiler.diagnostic.Kind.SEVERE_WARNING, "不确定的对象类型");
							}
						}
						dvc = true;
						break checkFirstArg;
					}
				}

				var maybeValue = cast(bound, componentType);
				if (maybeValue.type < _minCastAllow)
					return cast != null
						? FAIL(i, bound, type, cast)
						: FAIL(i, bound, componentType, maybeValue);
				cast = maybeValue;
			}
			distance += cast.distance;
			i++;

			for (;i < inSize; i++) {
				IType from = input.get(i);
				cast = cast(from, componentType);
				if (cast.type < _minCastAllow)
					return FAIL(i, from, componentType, cast);

				bound = ctx.getCommonParent(from, bound);
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
				case TypeCast.LOSSY: distance += LEVEL_DEPTH; break;
				case TypeCast.IMPLICIT: if (_minCastAllow < 0) {distance += cast.distance; break;}
				default: return FAIL(i, from, to, cast);

				// 装箱/拆箱 第二步
				case TypeCast.UNBOXING: case TypeCast.BOXING: hasBox = true;
				case TypeCast.NUMBER_UPCAST: case TypeCast.UPCAST: distance += cast.distance; break;
			}

			if (sign != null) bounds[i] = from;
		}

		if (hasBox) distance += LEVEL_DEPTH;

		return parameterized(new MethodResult(mn, distance, dvc));
	}

	/**
	 * 警告: override mode现在不检查参数数量
	 */
	public MethodResult inferOverride(CompileContext ctx, MethodNode overriden, Type type, List<IType> argument) {
		overrideMode = true;
		var mr = new MethodListSingle(overriden, false).findMethod(ctx, type, argument, 0);
		overrideMode = false;
		return mr;
	}
	// TODO 暂时能跑，不过能cover 100%么
	public TypeCast.Cast overrideCast(IType from, IType to) {
		if (to instanceof TypeVariable n) to = typeParams.get(n.name);
		if (from.equals(to)) return TypeCast.RESULT(0, 0);

		if (from.kind() <= IType.PARAMETERIZED_TYPE) {
			if (!from.isPrimitive() && !to.isPrimitive()) {
				// <?> or AnyType
				if (to.kind() == IType.UNBOUNDED_WILDCARD) {
					if (from.owner().equals("java/lang/Object") && from.array() == 0) {
						return TypeCast.RESULT(0, 0);
					}
				} else if (to.kind() == IType.BOUNDED_WILDCARD) {
					return TypeCast.RESULT(0, 0);
				} else if (from.owner().equals(to.owner())) {
					// Generic cast check
					return castChecker.checkCast(from, to);
				}
			}
		} else {
			LavaCompiler.debugLogger().warn("Unknown from type: "+from);
		}

		return TypeCast.ERROR(TypeCast.IMPOSSIBLE);
	}
	private TypeCast.Cast cast(IType from, IType to) {
		int lambdaArgCount = Lambda.getLambdaArgc(from);
		if (lambdaArgCount >= 0) {
			if (lambdaArgCount == Lambda.ARGC_UNKNOWN)
				return TypeCast.RESULT(TypeCast.UPCAST, 0);

			if (!to.isPrimitive()) {
				var mn = ctx.compiler.link(ctx.resolve(to)).getLambdaMethod();
				if (mn != null) {
					if (mn.parameters().size() == lambdaArgCount) {
						List<IType> fromArgs = Lambda.getLambdaArgs(from);
						if (fromArgs == null) return TypeCast.RESULT(TypeCast.UPCAST, 0);

						List<IType> toArgs = ctx.inferGeneric(to, mn).parameters();
						int distance = 0;
						for (int i = 0; i < lambdaArgCount; i++) {
							var cast = castChecker.checkCast(fromArgs.get(i), toArgs.get(i));
							if (cast.type < 0) return TypeCast.RESULT(-96, lambdaArgCount);

							distance += cast.distance;
						}
						return TypeCast.RESULT(TypeCast.UPCAST, distance);
					}
				}
			}
			return TypeCast.RESULT(-99/*无效的函数接口; 仅定义在i18n中*/, lambdaArgCount);
		}

		return castChecker.checkCast(from, to);
	}
	private static MethodResult FAIL(int pos, IType from, IType to, TypeCast.Cast cast) { return new MethodResult(cast.type, from, to, pos); }

	private MethodResult parameterized(MethodResult r) {
		if (sign == null) return r;

		for (int i = 0; i < sign.values.size()-1; i++) {
			if (bounds[i] == null) continue;
			try {
				mergeBounds(bounds[i], sign.values.get(i));
			} catch (UnableCastException e) {
				return FAIL(i,e.from,e.to,e.code);
			}
			bounds[i] = null;
		}
		substituteMissingTypeParametersToBound(typeParamBounds, typeParams);

		if (!sign.values.isEmpty()) {
			r.desc = new IType[sign.values.size()];
			List<IType> values = sign.values;
			for (int i = 0; i < values.size(); i++) {
				r.desc[i] = substitute(values.get(i));
			}
		}

		if (!sign.exceptions.isEmpty()) {
			r.exception = new IType[sign.exceptions.size()];
			List<IType> values = sign.exceptions;
			for (int i = 0; i < values.size(); i++) {
				r.exception[i] = substitute(values.get(i));
			}
		}

		return r;
	}
	private IType substitute(IType type) { return substituteTypeVariables(type, typeParams, typeParamBounds); }

	@SuppressWarnings("fallthrough")
	@Contract(pure = true)
	public static boolean isFullyResolved(IType type) {
		switch (type.kind()) {
			case IType.TYPE_VARIABLE: return ((TypeVariable) type).wildcard == ParameterizedType.NO_WILDCARD;
			case IType.UNBOUNDED_WILDCARD: return false;
			case IType.PARAMETERIZED_TYPE: if (((ParameterizedType) type).wildcard != ParameterizedType.NO_WILDCARD) return false;
			case IType.PARAMETERIZED_CHILD:
				IGeneric t = (IGeneric) type;
				for (int i = 0; i < t.typeParameters.size(); i++)
					if (!isFullyResolved(t.typeParameters.get(i)))
						return false;
				if (t.sub != null && containsTypeVariable(t.sub))
					return false;
		}

		return true;
	}

	@Contract(pure = true)
	public static boolean containsTypeVariable(IType type) {
		switch (type.kind()) {
			case IType.TYPE_VARIABLE: return true;

			case IType.PARAMETERIZED_TYPE, IType.PARAMETERIZED_CHILD:
				IGeneric t = (IGeneric) type;
				for (int i = 0; i < t.typeParameters.size(); i++)
					if (containsTypeVariable(t.typeParameters.get(i)))
						return true;
				if (t.sub != null && containsTypeVariable(t.sub))
					return true;
		}

		return false;
	}
	/**
	 * Creates a substitution map from type variables to their actual arguments.
	 * @throws IllegalArgumentException if sizes mismatch.
	 */
	@Contract(pure = true, value = "_, _ -> new")
	public static Map<String, IType> createSubstitutionMap(Map<String, List<IType>> typeVariables, List<IType> typeParameters) {
		if (typeParameters.size() != typeVariables.size())
			throw new IllegalArgumentException("类型参数的长度("+typeParameters.size()+") != 类型变量的长度("+typeVariables.size()+")");

		Map<String, IType> output = new HashMap<>(typeParameters.size());
		int i = 0;
		for (String name : typeVariables.keySet()) {
			output.put(name, typeParameters.get(i++));
		}

		return output;
	}
	/**
	 * Infers wildcard bounds (e.g., ? extends T) for missing type variable substitutions.
	 */
	public static void substituteMissingTypeParametersToBound(Map<String, List<IType>> typeVariables, Map<String, IType> typeParameters) {
		for (Map.Entry<String, List<IType>> entry : typeVariables.entrySet()) {
			if (!typeParameters.containsKey(entry.getKey())) {
				var value = entry.getValue();
				// 这里返回any的反向 - nullType => [? extends T] 可以upcast转换为任意继承T的对象，而不是downcast
				typeParameters.put(entry.getKey(), new WildcardType(value.get(0).kind() == IType.OBJECT_BOUND ? value.subList(1, value.size()) : value));
			}
		}
	}
	/**
	 * Substitutes type variables with their actual types from the substitution map.
	 * Handles cloning for mutations, wildcards, and array dimensions.
	 * @return The resolved type (may be cloned if modified).
	 */
	@Contract(pure = true)
	public static IType substituteTypeVariables(IType type, Map<String, IType> substitution, Map<String, List<IType>> typeVariables) {
		switch (type.kind()) {
			case IType.TYPE_VARIABLE -> {
				TypeVariable variable = (TypeVariable) type;

				var actualType = substitution.get(variable.name);
				if (actualType == null) throw new IllegalArgumentException("缺少类型参数"+variable);

				if (actualType.kind() == IType.BOUNDED_WILDCARD) return actualType;
				if (actualType.kind() == IType.UNBOUNDED_WILDCARD) return Types.OBJECT_TYPE;//Asterisk.anyType;

				if (variable.array() != actualType.array()) throw new IllegalArgumentException("数组层次错误: "+variable+" cannot cast to "+actualType);
				/*if (variable.array() > 0) {
					actualType = actualType.clone();
					actualType.setArrayDim(variable.array());
				}*/

				var bound = typeVariables.get(variable.name);
				if (variable.wildcard == ParameterizedType.SUPER_WILDCARD) {
					LavaCompiler.debugLogger().warn("EX_SUPER how to deal? typeParam={}, substitution={}", variable, actualType);
					return actualType;
				}

				return WildcardType.genericReturn(actualType, bound.get(bound.get(0).kind() == IType.OBJECT_BOUND ? 1 : 0));
			}
			case IType.BOUNDED_WILDCARD -> {
				WildcardType t = (WildcardType) type;
				List<IType> traits = t.getTraits();
				for (int i = 0; i < traits.size(); i++) {
					IType prev = traits.get(i);
					IType elem = substituteTypeVariables(prev, substitution, typeVariables);

					if (prev != elem) {
						if (type == t) {
							t = t.clone();
							traits = t.getTraits();
						}

						traits.set(i, elem);
					}
				}

				return t;
			}
			case IType.PARAMETERIZED_TYPE, IType.PARAMETERIZED_CHILD -> {
				IGeneric t = (IGeneric) type;
				List<IType> parameters = t.typeParameters;
				for (int i = 0; i < parameters.size(); i++) {
					IType prev = parameters.get(i);
					IType elem = substituteTypeVariables(prev, substitution, typeVariables);

					if (prev != elem) {
						if (type == t) {
							t = t.clone();
							parameters = t.typeParameters;
						}

						parameters.set(i, elem);
					}
				}

				if (t.sub != null) {
					var elem = substituteTypeVariables(t.sub, substitution, typeVariables);
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

	private void mergeBounds(IType targetType, IType inputType) {
		switch (inputType.kind()) {
			default: throw new UnsupportedOperationException("out="+targetType+",in="+inputType);
			case Type.SIMPLE_TYPE, Type.OBJECT_BOUND, Type.UNBOUNDED_WILDCARD: return;
			case Type.PARAMETERIZED_TYPE:
				// Asterisk
				if (!(targetType instanceof ParameterizedType pSide)) return;

				ParameterizedType mSide = (ParameterizedType) inputType;
				List<IType> children = pSide.typeParameters;
				if (children.size() != mSide.typeParameters.size()) throw new UnsupportedOperationException("GEN PARAM ERROR");
				for (int i = 0; i < children.size(); i++) {
					mergeBounds(children.get(i), mSide.typeParameters.get(i));
				}
				if (mSide.sub != null) mergeBounds(pSide.sub, mSide.sub);
			break;
			case Type.TYPE_VARIABLE:
				TypeVariable tp = (TypeVariable) inputType;
				if (targetType.isPrimitive() && !isPrimitiveVariable(tp))
					targetType = Type.getWrapper(targetType.rawType());
				addBound(tp, targetType);
			break;
		}
	}
	private boolean isPrimitiveVariable(TypeVariable tp) {
		IType type = typeParams.get(tp.name);
		// TODO
		return false;
	}
	private void addBound(TypeVariable tp, IType type) {
		IType bound = typeParams.get(tp.name);
		if (tp.wildcard != ParameterizedType.NO_WILDCARD) {
			type = new ParameterizedType(type.owner(), type.array()+tp.array(), tp.wildcard);
		} else if (tp.array() != 0) {
			type = type.clone();
			type.setArrayDim(tp.array());
		}

		// equals是为了防止出现两个null type (但是可以出现两个asterisk type)
		bound = bound == null || type.equals(bound) ? type : getCommonChild(type, bound);
		typeParams.put(tp.name, bound);
	}
	// TODO 优化，使用FrameVisitor那边的实现
	private IType getCommonChild(IType a, IType b) {
		TypeCast.Cast cast = cast(a, b);
		if (cast.type >= 0) return b; // a更不具体
		cast = cast(b, a);
		if (cast.type >= 0) return a; // b更不具体

		throw new UnableCastException(a, b, cast);
	}
}