package roj.reflect;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.*;
import roj.collect.ArrayList;
import roj.collect.HashMap;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 运行时泛型推断
 * @author Claude Sonnet 4
 * @since 2025/09/19 18:05
 */
public class RuntimeTypeInference {
	/**
	 * 实现细节上和{@link roj.compiler.resolve.Resolver#inferGeneric(IType, String)}还是有些不同的，主要集中在一些边界情况，例如尽可能返回有意义的值
	 * 比如typeInst == targetType时返回边界
	 */
	public static List<Type> inferGeneric(Type typeInst, Class<?> targetType) {
		// Get the raw class from typeInst
		Class<?> rawClass = getRawClass(typeInst);

		// Check if targetType is in the inheritance hierarchy
		if (!targetType.isAssignableFrom(rawClass)) {
			throw new IllegalArgumentException(typeInst+" is not assignable to "+targetType);
		}

		// If targetType has no type parameters, return empty list
		if (targetType.getTypeParameters().length == 0) {
			return Collections.emptyList();
		}

		// If targetType is the same as rawClass, return direct type arguments
		if (targetType.equals(rawClass)) {
			return getTypeArguments(typeInst, targetType);
		}

		// Resolve type arguments through inheritance hierarchy
		Map<java.lang.reflect.TypeVariable<?>, Type> typeContext = buildTypeContext(typeInst, rawClass);
		return resolveTargetTypeArguments(rawClass, targetType, typeContext);
	}

	@NotNull
	public static Class<?> getRawClass(Type type) {
		if (type instanceof Class<?>) {
			return (Class<?>) type;
		} else if (type instanceof java.lang.reflect.ParameterizedType) {
			return (Class<?>) ((java.lang.reflect.ParameterizedType) type).getRawType();
		} else if (type instanceof java.lang.reflect.TypeVariable<?> tv) {
			Type[] bounds = tv.getBounds();
			return bounds.length > 0 ? getRawClass(bounds[0]) : Object.class;
		} else if (type instanceof WildcardType wt) {
			Type[] upperBounds = wt.getUpperBounds();
			return upperBounds.length > 0 ? getRawClass(upperBounds[0]) : Object.class;
		}
		throw new AssertionError();
	}

	public static List<Type> getTypeArguments(Type typeInst, Class<?> targetClass) {
		if (typeInst instanceof java.lang.reflect.ParameterizedType paramType) {
			return Arrays.asList(paramType.getActualTypeArguments());
		} else {
			// Raw type - return upper bounds of type parameters
			java.lang.reflect.TypeVariable<?>[] typeParams = targetClass.getTypeParameters();
			List<Type> bounds = new ArrayList<>();
			for (java.lang.reflect.TypeVariable<?> tv : typeParams) {
				Type[] paramBounds = tv.getBounds();
				bounds.add(paramBounds.length > 0 ? paramBounds[0] : Object.class);
			}
			return bounds;
		}
	}

	private static Map<java.lang.reflect.TypeVariable<?>, Type> buildTypeContext(Type typeInst, Class<?> rawClass) {
		Map<java.lang.reflect.TypeVariable<?>, Type> context = new HashMap<>();
		if (typeInst instanceof java.lang.reflect.ParameterizedType paramType) {
			java.lang.reflect.TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
			Type[] typeArgs = paramType.getActualTypeArguments();
			for (int i = 0; i < Math.min(typeParams.length, typeArgs.length); i++) {
				context.put(typeParams[i], typeArgs[i]);
			}
		}
		return context;
	}

	private static List<Type> resolveTargetTypeArguments(Class<?> currentClass, Class<?> targetType,
														 Map<java.lang.reflect.TypeVariable<?>, Type> context) {
		// Check superclass
		Type genericSuperclass = currentClass.getGenericSuperclass();
		if (genericSuperclass != null) {
			List<Type> result = tryResolveFromType(genericSuperclass, targetType, context);
			if (result != null) {
				return result;
			}
		}

		// Check interfaces
		Type[] genericInterfaces = currentClass.getGenericInterfaces();
		for (Type genericInterface : genericInterfaces) {
			List<Type> result = tryResolveFromType(genericInterface, targetType, context);
			if (result != null) {
				return result;
			}
		}

		return null;
	}

	private static List<Type> tryResolveFromType(Type type, Class<?> targetType,
												 Map<java.lang.reflect.TypeVariable<?>, Type> context) {
		Class<?> rawType = getRawClass(type);

		if (targetType.equals(rawType)) {
			// Found the target - resolve its type arguments
			if (type instanceof java.lang.reflect.ParameterizedType paramType) {
				Type[] typeArgs = paramType.getActualTypeArguments();
				List<Type> resolvedArgs = new ArrayList<>();
				for (Type arg : typeArgs) {
					resolvedArgs.add(resolveTypeVariable(arg, context));
				}
				return resolvedArgs;
			} else {
				// Raw target type - return bounds
				return getTypeArguments(type, targetType);
			}
		} else if (targetType.isAssignableFrom(rawType)) {
			// Continue searching in this branch
			Map<java.lang.reflect.TypeVariable<?>, Type> newContext = new HashMap<>(context);

			// Update context with mappings from this level
			if (type instanceof java.lang.reflect.ParameterizedType paramType) {
				java.lang.reflect.TypeVariable<?>[] typeParams = rawType.getTypeParameters();
				Type[] typeArgs = paramType.getActualTypeArguments();
				for (int i = 0; i < Math.min(typeParams.length, typeArgs.length); i++) {
					newContext.put(typeParams[i], resolveTypeVariable(typeArgs[i], context));
				}
			}

			return resolveTargetTypeArguments(rawType, targetType, newContext);
		}

		return null;
	}

	private static Type resolveTypeVariable(Type type, Map<java.lang.reflect.TypeVariable<?>, Type> context) {
		if (type instanceof java.lang.reflect.TypeVariable<?> tv) {
			Type resolved = context.get(tv);
			if (resolved != null) return resolved;

			// If not found in context, return first bound
			Type[] bounds = tv.getBounds();
			return bounds.length > 0 ? bounds[0] : Object.class;
		}

		// For non-type-variables, return as-is
		return type;
	}

	public static Type fromAsmType(IType asmType, ClassLoader classLoader) throws ClassNotFoundException {
		Type result = asmType.rawType().toClass(classLoader);
		if (asmType instanceof IGeneric generic) {
			Type[] array = getTypes(classLoader, generic);

			result = new ParameterizedTypeImpl(result, array, null);
			while (generic.sub != null) {
				Class<?> aClass = Class.forName(result.getTypeName() + "$" + generic.sub.owner, false, classLoader);
				result = new ParameterizedTypeImpl(aClass, getTypes(classLoader, generic), result);

				generic = generic.sub;
			}
		}
		return result;
	}

	private static Type[] getTypes(ClassLoader classLoader, IGeneric generic) throws ClassNotFoundException {
		List<IType> children = generic.typeParameters;

		Type[] array = new Type[children.size()];
		for (int i = 0; i < children.size(); i++) {
			array[i] = fromAsmType(children.get(i), classLoader);
		}
		return array;
	}

	public static IType toAsmType(Type type) {
		if (type instanceof Class<?> c) return roj.asm.type.Type.getType(c);
		if (type instanceof java.lang.reflect.ParameterizedType parameterizedType) {
			var rawType = roj.asm.type.Type.getType(((Class<?>) parameterizedType.getRawType()));
			Type ownerType = parameterizedType.getOwnerType();
			if (ownerType != null) throw new UnsupportedOperationException("not implemented for "+type);
			ParameterizedType generic = new ParameterizedType(rawType.owner, rawType.array(), ParameterizedType.NO_WILDCARD);
			for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
				generic.addChild(toAsmType(actualTypeArgument));
			}
			return generic;
		}
		throw new UnsupportedOperationException("unsupported type "+type);
	}

	/**
	 * @see roj.compiler.resolve.Inferrer#substituteTypeVariables(IType, Map, Map)
	 */
	public static IType substituteTypeVariables(IType type, Function<TypeVariable, IType> mapper) {
		switch (type.kind()) {
			case IType.TYPE_VARIABLE -> {
				return mapper.apply((TypeVariable) type);
			}
			case IType.PARAMETERIZED_TYPE, IType.PARAMETERIZED_CHILD -> {
				IGeneric t = (IGeneric) type;
				List<IType> typeParameters = t.typeParameters;
				for (int i = 0; i < typeParameters.size(); i++) {
					IType prev = typeParameters.get(i);
					IType elem = substituteTypeVariables(prev, mapper);

					if (prev != elem) {
						if (type == t) {
							t = t.clone();
							typeParameters = t.typeParameters;
						}

						typeParameters.set(i, elem);
					}
				}

				if (t.sub != null) {
					var elem = substituteTypeVariables(t.sub, mapper);
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

	public static final class ParameterizedTypeImpl implements java.lang.reflect.ParameterizedType {
		private final Type[] actualTypeArguments;
		private final Type rawType;
		private final Type ownerType;

		ParameterizedTypeImpl(Type raw, Type[] args, Type owner) {
			this.rawType = raw;
			this.actualTypeArguments = args;
			this.ownerType = owner;
		}

		@Override public String getTypeName() {return rawType.getTypeName();}
		@Override public Type[] getActualTypeArguments() {return actualTypeArguments;}
		@Override public Type getRawType() {return rawType;}
		@Override public Type getOwnerType() {return ownerType;}
	}
}
