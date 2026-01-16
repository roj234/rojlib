package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.function.UnaryOperator;

/**
 * 类型表示
 * Type Representation
 */
public interface IType extends Cloneable {
	/**
	 * Simple concrete types, including primitives (e.g., {@code int}), classes (e.g., {@code String}),
	 * and arrays (e.g., {@code Object[]}). These are fully resolved and non-generic.
	 * <p>
	 * Casting: Serves as the base for all conversions. Upcasts to supertypes; downcasts require exact match or subtyping.
	 * Primitives support boxing/unboxing to wrappers (e.g., {@code int} to {@code Integer}).
	 * </p>
	 * Example: {@code int}, {@code java.lang.Object}, {@code String[]}.
	 * @see Type
	 */
	byte
	SIMPLE_TYPE = 0,
	/**
	 * Parameterized (instantiated) generic types, where type parameters are resolved.
	 * <p>
	 * Casting: Behaves like its raw type for upcasting but preserves generics for downcasting.
	 * Cannot directly cast to/from uninstantiated generics; resolves via type arguments.
	 * Interacts with {@link ParameterizedType#EXTENDS_WILDCARD WildcardType} for bounded parameters (e.g., {@code List<? extends Number>}).
	 * </p>
	 * Example: {@code List<String>}, {@code Map<Integer, String>}.
	 * @see ParameterizedType
	 */
	PARAMETERIZED_TYPE = 1,
	/**
	 * Type variables (type parameters), which are placeholders during generic declaration or inference.
	 * They have optional bounds (e.g., upper bounds via {@code extends}) and can be arrays.
	 * <p>
	 * Casting: Resolved to their bound during checks.
	 * </p>
	 * Example: {@code T} in {@code class Box<T>}, {@code Val extends Number}, {@code T[]}.
	 * @see #BOUNDED_WILDCARD
	 * @see TypeVariable
	 */
	TYPE_VARIABLE = 2,
	/**
	 * Unbounded wildcard, representing an unknown type with no constraints (equivalent to {@code ?} in Java).
	 * Used for completely flexible type positions, like in method signatures.
	 * <p>
	 * Casting: Allows upcast from any type (widest acceptance); downcast to specifics is unsafe without checks.
	 * </p>
	 * Example: {@code ?} in {@code List<?>}
	 * @implNote Singleton class
	 * @see Signature#unboundedWildcard()
	 */
	UNBOUNDED_WILDCARD = 3,
	/**
	 * Placeholder for the implicit upper bound of {@code java.lang.Object} in type variables.
	 * Appears as the first bound when the primary bound is an interface (e.g., {@code T extends Runnable}).
	 * Effectively equivalent to {@code Object} represent as a placeholder as JVMS.
	 * <p>
	 * Casting: AssertionError
	 * </p>
	 * Example: In {@code <T extends Interface>}, inserts as {@code Object} placeholder at index 0.
	 * @implNote Singleton class
	 * @see Signature#objectBound()
	 * @see Signature#typeVariables
	 */
	OBJECT_BOUND = 4,
	/**
	 * 已弃用，参数子类，结构现已更改
	 * @deprecated 结构现已更改
	 */
	PARAMETERIZED_CHILD = 5,
	/**
	 * Bounded wildcard with an intersection upper bound (e.g., {@code ? extends A & B}).
	 * Can be upcast to any of its bounds, but downcasting to this type requires the source to satisfy <em>all</em> bounds.
	 * Represents multiple upper bounds conjoined with {@code &} (intersection type).
	 * <p>
	 * Casting: Checks all bounds for downcast; upcast succeeds if source matches any bound.
	 * </p>
	 * Example: {@code ? extends Number & Serializable} – accepts subtypes of both.
	 * @apiNote Not literally "? extends A & B"
	 */
	// maybe rename => INTERSECTION_TYPE
	BOUNDED_WILDCARD = 6,
	/**
	 * Captured wildcard, representing an inferred or "erased" type during generic operations.
	 * The actual runtime type is often {@code Object}, but compile-time allows direct conversion to the inferred type.
	 * Used for wildcards in method invocations or lambda captures.
	 * <p>
	 * Casting: Allows "free" downcast to the capture's visual/inferred type (e.g., from {@code Object} to {@code String} in {@code List<String>.get()}).
	 * </p>
	 * Example: Return type of {@code list.get(0)} in generic {@code List<T>}, captured as {@code T} but writes as {@code Object}.
	 */
	CAPTURED_WILDCARD = 7,
	/**
	 * Union type (disjoint or "or" type), where the type can be any of the alternatives.
	 * Cannot be directly downcast from (no single source matches all), but any alternative can upcast to it.
	 * <p>
	 * Casting: Succeeds if source casts to <em>any</em> bound; downcast checks all possibilities (often ERROR if ambiguous).
	 * </p>
	 * Example: {@code String | Integer} – accepts upcast from either, but requires checks for downcast.
	 */
	UNION_TYPE = 8;

	@MagicConstant(intValues = {SIMPLE_TYPE, PARAMETERIZED_TYPE, TYPE_VARIABLE, UNBOUNDED_WILDCARD, OBJECT_BOUND, PARAMETERIZED_CHILD, BOUNDED_WILDCARD, CAPTURED_WILDCARD, UNION_TYPE})
	@Contract(pure = true) byte kind();

	/**
	 * 返回该类型的Java内部表示形式.
	 * @return 该类型的Java内部表示形式
	 */
	@Contract(pure = true) default String toDesc() {
		var sb = IOUtil.getSharedCharBuf();
		toDesc(sb);
		return sb.toString();
	}
	@Contract(pure = true) void toDesc(CharList sb);
	@Contract(pure = true) void toString(CharList sb);

	byte E_TYPE_VARIABLE = 0, E_ARGUMENT = 1, E_RETURN = 2, E_THROW = 3, E_PARAMETERIZED = 4;
	@Contract(pure = true) default void validate(@MagicConstant(intValues = {E_TYPE_VARIABLE, E_ARGUMENT, E_RETURN, E_THROW, E_PARAMETERIZED}) int positionType, int index) {}

	@Contract(pure = true) default boolean isPrimitive() { return false; }
	@Contract(pure = true) default int getActualType() { return Type.OBJECT; }

	@Contract(pure = true) default Type rawType() { throw new UnsupportedOperationException(getClass().getName()); }
	@Contract(pure = true) default int array() { return 0; }
	default void setArrayDim(int array) { throw new UnsupportedOperationException(getClass().getName()); }

	@Contract(pure = true) default String owner() { throw new UnsupportedOperationException(getClass().getName()); }
	default void owner(String owner) { throw new UnsupportedOperationException(getClass().getName()); }

	default void rename(UnaryOperator<String> fn) {}

	IType clone();
}