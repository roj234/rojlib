package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to enable implicit type conversion rules for the annotated type, allowing
 * the compiler to automatically insert conversions during type checking, assignments, or
 * argument passing without explicit casts. This annotation supports both direction-based
 * conversions depending on the method signature, inspired by Rust's {@code Into} and
 * {@code From} traits but unified into a single annotation.
 * <p>
 * <strong>Usage Semantics:</strong>
 * <ul>
 * <li><strong>Instance Methods:</strong> Treated as a "to" conversion from the receiver type
 *     ({@code this}) to the method's return type. The method acts as an implicit exporter,
 *     enabling upcasts or coercions to the target (e.g., {@code MyType} implicitly convertible
 *     to {@code long}).</li>
 * <li><strong>Static Methods:</strong> Treated as a "from" conversion from the first parameter
 *     type to the return type. The method acts as an implicit importer, allowing conversions
 *     from the source type to the annotated class (e.g., {@code long} implicitly convertible
 *     to {@code MyType}). Multi-parameter static methods are unsupported or require explicit
 *     source specification via future extensions.</li>
 * </ul>
 * Method names are conventionally prefixed (e.g., {@code toLong()} for instance,
 * {@code fromLong(long)} for static), but the compiler infers direction from the signature
 * rather than the name.
 * <p>
 * Conversions are inserted at compile-time for type safety and may involve boxing/unboxing
 * for primitives. Conflicts (e.g., multiple applicable conversions) are resolved by
 * specificity or ambiguity errors. .
 * <p>
 * By default, conversions are <em>implicit</em>, applicable in any compatible context.
 * Set {@link #explicit()} to {@code true} to restrict to explicit casts only, useful
 * for unsafe or runtime-dependent conversions.
 *
 * @apiNote This annotation is processed by the compiler during type resolution and does not
 *          generate runtime code. Ensure methods are non-ambiguous and performant.
 * @implNote Does not support Parameterized type (to raw type now)
 * @implNote Not implemented yet.
 * @author Roj234
 * @since 2025/10/21 03:52
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface Into {
	/**
	 * Determines if the conversion requires explicit casting.
	 *
	 * @return {@code true} for explicit-only, {@code false} for implicit (default).
	 */
	boolean explicit() default false;
}