package roj.compiler.resolve;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
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
import roj.compiler.diagnostic.IText;
import roj.compiler.diagnostic.Kind;
import roj.compiler.types.CompoundType;
import roj.compiler.types.LambdaForm;
import roj.util.ArrayCache;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 方法重载(overload)和覆盖(override)有关的判断
 * @author Roj234
 * @since 2024/1/28 11:59
 */
public final class Inferrer {
	public static final MethodResult
		FAIL_ARGCOUNT = new MethodResult(-97, ArrayCache.OBJECTS),
		FAIL_GENERIC = new MethodResult(TypeCast.GENERIC_PARAM_COUNT, ArrayCache.OBJECTS);

	private static final int LEVEL_DEPTH = 5120;

	private final CompileContext ctx;
	private final TypeCast castChecker;

	// 类型参数的当前类型
	private final Map<TypeVariableDeclaration, IType> typeParams = new HashMap<>();
	//private final Map<TypeVariableDeclaration, List<IType>> constrain;

	@Nullable
	private Signature sign;
	private IType[] constrains;

	public Inferrer(CompileContext ctx) {
		this.ctx = ctx;
		this.castChecker = new TypeCast(ctx.compiler);
		this.castChecker.genericContext = typeParams;
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
	public MethodResult resolveInvocation(ClassNode declaringClass, MethodNode method, IType instanceType, List<IType> arguments) {
		List<? extends IType> mpar;
		int mnSize;

		typeParams.clear();

		sign = method.getAttribute(declaringClass, Attribute.SIGNATURE);
		if (sign != null) {
			int len = sign.values.size()-1;
			if (constrains == null || constrains.length < len) constrains = new IType[len];

			var myTP = sign.typeVariables;

			if (manualTPBounds != null) {
				if (manualTPBounds.size() != myTP.size()) return FAIL_ARGCOUNT;

				int i = 0;
				for (var decl : myTP)
					typeParams.put(decl, manualTPBounds.get(i++));
			}

			Signature classSign = declaringClass.getAttribute(Attribute.SIGNATURE);
			if (classSign != null && instanceType != null) {
				if (!instanceType.owner().equals(declaringClass.name())) {
					List<IType> myBounds = ctx.compiler.inferGeneric(instanceType, declaringClass.name());

					assert myBounds != null;
					ParameterizedType g = new ParameterizedType(declaringClass.name(), ParameterizedType.NO_WILDCARD);
					g.typeParameters = myBounds;
					instanceType = g;
				} else if (instanceType.kind() == IType.CAPTURED_WILDCARD) {
					// 解决了问题，但是它*必须*放在这里么
					instanceType = ((CompoundType) instanceType).getBound();
				}

				boundPreCheck:
				if (instanceType instanceof ParameterizedType gHint) {
					// TODO 非静态 泛型 内部类
					assert gHint.sub == null : "dynamic subclass not supported at this time";
					if (gHint.typeParameters.size() != classSign.typeVariables.size()) {
						if (gHint.typeParameters.size() == 1 && gHint.typeParameters.get(0) == Types.anyGeneric)
							break boundPreCheck;

						return FAIL_GENERIC;
					}

					int i = 0;
					for (var decl : classSign.typeVariables) {
						typeParams.putIfAbsent(decl, gHint.typeParameters.get(i++));
					}
				}
			}

			mpar = sign.values;
			mnSize = mpar.size()-1;
		} else {
			if (manualTPBounds != null) return FAIL_ARGCOUNT;

			mpar = method.parameters();
			mnSize = mpar.size();
		}

		int distance = 0;
		int inSize = arguments.size();
		boolean dvc = false;

		if (!overrideMode)
		checkSpecial:
		if ((method.modifier&Opcodes.ACC_VARARGS) != 0) {
			int vararg = mnSize-1;
			if (inSize < vararg) return FAIL_ARGCOUNT;

			// varargs是最后一步
			distance += LEVEL_DEPTH*2;

			IType type = mpar.get(vararg);
			if (type.array() == 0) throw ResolveException.ofIllegalInput("semantic.resolution.illegalVararg", method.owner(), method.name());
			IType componentType = TypeHelper.componentType(type);

			if (inSize == vararg) {
				if (vararg == 0)
					distance -= cast(componentType, Types.OBJECT_TYPE).distance;
				break checkSpecial;
			}

			int i = vararg;
			IType bound = arguments.get(i);
			TypeCast.Cast cast = null;
			checkFirstArg: {
				if (i+1 == inSize) {
					cast = cast(bound, type);
					if (cast.type >= 0) {
						if (type.array() == 1 && "java/lang/Object".equals(type.owner())) {
							if (!type.equals(bound)) {
								ctx.report(roj.compiler.diagnostic.Kind.SEVERE_WARNING, "对象数组被赋予了不确定的类型");
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
				IType from = arguments.get(i);
				cast = cast(from, componentType);
				if (cast.type < _minCastAllow)
					return FAIL(i, from, componentType, cast);

				bound = ctx.compiler.getCommonAncestor(from, bound);
				distance += cast.distance;
			}

			if (sign != null) {
				bound = bound.clone();
				bound.setArrayDim(bound.array()+1);
				constrains[vararg] = bound;
			}
			inSize = vararg;
		} else if (mnSize != inSize) return FAIL_ARGCOUNT;

		boolean hasBoxing = false;
		for (int i = 0; i < inSize; i++) {
			IType from = arguments.get(i), to = mpar.get(i);

			var cast = overrideMode ? overrideCast(from, to) : cast(from, to);
			switch (cast.type) {
				case TypeCast.LOSSY: distance += LEVEL_DEPTH; break;
				case TypeCast.IMPLICIT: if (_minCastAllow < 0) {distance += cast.distance; break;}
				default: return FAIL(i, from, to, cast);

				// 装箱/拆箱 第二步
				case TypeCast.UNBOXING: case TypeCast.BOXING: hasBoxing = true;
				case TypeCast.NUMBER_UPCAST: case TypeCast.UPCAST: distance += cast.distance; break;
			}

			if (sign != null) constrains[i] = from;
		}

		if (hasBoxing) distance += LEVEL_DEPTH;

		return applyInferredTypes(new MethodResult(method, distance, dvc));
	}

	public MethodResult getSubstitutedParameters(ClassNode declaringClass, MethodNode method, @Nullable IType instanceType) {
		overrideMode = true;
		MethodResult mr = resolveInvocation(declaringClass, method, instanceType instanceof ParameterizedType g ? g : null, Collections.emptyList());
		if (mr.distance < 0) {
			throw new IllegalStateException("Unexpected error: "+declaringClass.name()+"."+method+" on "+instanceType);
		}
		overrideMode = false;
		return mr;
	}

	/**
	 * 警告: override mode现在不检查参数数量
	 */
	public boolean validateOverrideGenericCompatibility(CompileContext ctx, MethodNode superMethod, Type instanceType, List<IType> subMethodArgs, List<IType> superMethodArgs) {
		ClassNode owner = ctx.compiler.resolve(superMethod.owner());

		if (!ctx.canAccessSymbol(owner, superMethod, false, true)) return false;

		int inSize = subMethodArgs.size();
		MethodResult error = null;

		for (int i = 0; i < inSize; i++) {
			IType from = subMethodArgs.get(i), to = superMethodArgs.get(i);

			var cast = ctx.inferrer.castChecker.checkCast(from, to);
			switch (cast.type) {
				default: {
					error = FAIL(i, from, to, cast);

					IText rest = IText.empty().append(
						IText.translatable("invoke.except").append(MethodList.renderParameters(superMethod)).append("\n")
					).append(
						MethodList.getFoundText(subMethodArgs).prepend("  ").append("\n")
					).append(
						IText.translatable("invoke.reason").append(MethodList.getReason(superMethod, instanceType, error)).prepend("  ").append("\n")
					);

					ctx.report(Kind.ERROR, "cu.override.unable", ctx.currentCodeBlockForReport(), ctx.resolve(superMethod.owner()), superMethod, rest);
				}
				case TypeCast.LOSSY, TypeCast.IMPLICIT, TypeCast.UNBOXING, TypeCast.NUMBER_UPCAST, TypeCast.UPCAST:
			}
		}

		MethodListSingle.checkDeprecation(ctx, owner, superMethod);
		return error == null;
	}

	@Deprecated
	private TypeCast.Cast overrideCast(IType from, IType to) {
		if (to instanceof TypeVariable n) to = typeParams.get(n.name());
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
			LavaCompiler.debugLogger().warn("Unknown from type: "+from+" | "+from.kind());
		}

		return TypeCast.ERROR(TypeCast.IMPOSSIBLE);
	}

	private TypeCast.Cast cast(IType from, IType to) {
		int lambdaArgCount = LambdaForm.getLambdaArgc(from);
		if (lambdaArgCount >= 0) return lambdaCast(from, to, lambdaArgCount);

		return castChecker.checkCast(from, to);
	}
	private TypeCast.Cast lambdaCast(IType from, IType to, int lambdaArgCount) {
		if (lambdaArgCount == LambdaForm.ARGC_UNKNOWN)
			return TypeCast.RESULT(TypeCast.UPCAST, 0);

		if (!to.isPrimitive()) {
			var lambdaMethod = ctx.compiler.link(ctx.resolve(to)).getLambdaMethod();
			if (lambdaMethod != null) {
				if (lambdaMethod.parameters().size() == lambdaArgCount) {
					List<IType> fromArgs = LambdaForm.getLambdaArgs(from);
					if (fromArgs == null) return TypeCast.RESULT(TypeCast.UPCAST, 0);

					List<IType> toArgs = ctx.inferGeneric(to, lambdaMethod).parameters();
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

	private static MethodResult FAIL(int pos, IType from, IType to, TypeCast.Cast cast) { return new MethodResult(cast.type, from, to, pos); }

	private MethodResult applyInferredTypes(MethodResult r) {
		if (sign == null) return r;

		for (int i = 0; i < sign.values.size()-1; i++) {
			if (constrains[i] == null) continue;
			try {
				constrainTypeVariables(constrains[i], sign.values.get(i));
			} catch (UnableCastException e) {
				return FAIL(i,e.from,e.to,e.code);
			}
			constrains[i] = null;
		}

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
	private IType substitute(IType type) { return substituteTypeVariables(type, typeParams); }

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
	public static Map<TypeVariableDeclaration, IType> createSubstitutionMap(Set<TypeVariableDeclaration> typeVariables, List<IType> typeParameters) {
		if (typeParameters.size() != typeVariables.size())
			throw new IllegalArgumentException("类型参数的长度("+typeParameters.size()+") != 类型变量的长度("+typeVariables.size()+")");

		Map<TypeVariableDeclaration, IType> output = new HashMap<>(typeParameters.size());
		int i = 0;
		for (var decl : typeVariables) {
			output.put(decl, typeParameters.get(i++));
		}

		return output;
	}

	private static @NotNull IType substituteToBound(TypeVariableDeclaration decl) {
		// 这里返回any的反向 - nullType => [? extends T] 可以upcast转换为任意继承T的对象，而不是downcast
		return CompoundType.intersection(decl.get(0).kind() == IType.OBJECT_BOUND ? decl.subList(1, decl.size()) : decl);
	}

	/**
	 * Substitutes type variables with their actual types from the substitution map.
	 * Handles cloning for mutations, wildcards, and array dimensions.
	 * @return The resolved type (may be cloned if modified).
	 */
	@Contract(pure = true)
	public static IType substituteTypeVariables(IType type, Map<TypeVariableDeclaration, IType> substitution) {
		switch (type.kind()) {
			case IType.TYPE_VARIABLE -> {
				TypeVariable variable = (TypeVariable) type;

				var actualType = substitution.get(variable.decl);
				if (actualType == null) {
					IType wildcardType = substituteToBound(variable.decl);
					if (variable.array() != 0) {
						wildcardType = wildcardType.clone();
						wildcardType.setArrayDim(wildcardType.array() + variable.array());
					}
					return wildcardType;
				}

				if (actualType.kind() == IType.UNBOUNDED_WILDCARD) return Types.OBJECT_TYPE;//Asterisk.anyType;
				if (actualType.kind() == IType.ANY_TYPE) return actualType;

				boolean cloned = false;
				// 260128: 此刻，我确实可以不带任何怀疑的写下这一行了（
				if (variable.array() != 0) {
					cloned = true;
					actualType = actualType.clone();
					actualType.setArrayDim(actualType.array() + variable.array());
				}

				if (variable.wildcard == ParameterizedType.SUPER_WILDCARD) {
					if (actualType instanceof ParameterizedType pt) {
						if (!cloned) pt = (ParameterizedType) pt.clone();
						pt.wildcard = ParameterizedType.SUPER_WILDCARD;
						return pt;
					}
					if (actualType instanceof Type simpleType) {
						return new ParameterizedType(simpleType.owner, simpleType.array(), ParameterizedType.SUPER_WILDCARD);
					}

					LavaCompiler.debugLogger().warn("EX_SUPER how to deal? typeParam={}, substitution={}", variable, actualType);
					return actualType;
				}

				if (actualType.kind() == IType.BOUNDED_WILDCARD) return actualType;

				var bound = variable.decl;
				return CompoundType.genericReturn(actualType, bound.get(bound.get(0).kind() == IType.OBJECT_BOUND ? 1 : 0));
			}
			case IType.BOUNDED_WILDCARD -> {
				CompoundType t = (CompoundType) type;
				List<IType> traits = t.getTraits();
				for (int i = 0; i < traits.size(); i++) {
					IType prev = traits.get(i);
					IType elem = substituteTypeVariables(prev, substitution);

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
					IType elem = substituteTypeVariables(prev, substitution);

					if (prev != elem) {
						if (type == t) {
							t = t.clone();
							parameters = t.typeParameters;
						}

						parameters.set(i, elem);
					}
				}

				if (t.sub != null) {
					var elem = substituteTypeVariables(t.sub, substitution);
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

	private void constrainTypeVariables(IType targetType, IType inputType) {
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
					constrainTypeVariables(children.get(i), mSide.typeParameters.get(i));
				}
				if (mSide.sub != null) constrainTypeVariables(pSide.sub, mSide.sub);
			break;
			case Type.TYPE_VARIABLE:
				TypeVariable tp = (TypeVariable) inputType;
				updateTypeVariableBound(tp, targetType);
			break;
		}
	}
	private boolean isPrimitiveVariable(TypeVariable tp) {
		IType type = typeParams.get(tp.decl);
		// TODO 这些似乎都可以不需要了？
		return false;
	}

	/**
	 * 基于新的constrain更新tv当前的类型(bound)
	 */
	private void updateTypeVariableBound(TypeVariable tv, IType constrain) {
		int arrayDim = constrain.array() - tv.array();
		assert arrayDim >= 0;

		IType bound = typeParams.get(tv.decl);
		/*if (tv.wildcard != ParameterizedType.NO_WILDCARD) {
			constrain = new ParameterizedType(constrain.owner(), arrayDim, tv.wildcard);
		} else */
		if (arrayDim != constrain.array()) {
			constrain = constrain.clone();
			constrain.setArrayDim(arrayDim);
		}

		if (constrain.isPrimitive() && !isPrimitiveVariable(tv))
			constrain = Type.getWrapper(constrain.rawType());

		// equals是为了防止出现两个null type (但是可以出现两个asterisk type)
		bound = bound == null || constrain.equals(bound) ? constrain : findMostSpecificType(constrain, bound);
		typeParams.put(tv.decl, bound);
	}
	// TODO 优化，使用FrameVisitor那边的实现
	private IType findMostSpecificType(IType a, IType b) {
		TypeCast.Cast cast = cast(a, b);
		if (cast.type >= 0) return b; // a更不具体
		cast = cast(b, a);
		if (cast.type >= 0) return a; // b更不具体

		throw new UnableCastException(a, b, cast);
	}
}