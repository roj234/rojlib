package roj.optimizer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.VarHandle;

/**
 * Marks a class or interface for optimized {@link VarHandle} access.
 *
 * <p>To support zero-context transforming:</p>
 * <ul>
 *   <li>The {@code VarHandle} instances must be directly load from / save to fields.</li>
 *   <li>For array-based {@code VarHandle}s, the field name must end with {@code $ARRAY}.</li>
 *   <li>For static field based {@code VarHandle}s, the field name must end with {@code $STATIC}.</li>
 * </ul>
 *
 * <p>Only a restricted set of {@code VarHandle} methods are permitted for use:
 * <code>get</code>, <code>set</code>, <code>compareAndSet</code> (and other atomic variants like
 * <code>compareAndExchange</code>), and similar direct accessors. Reflective or informational
 * methods such as <code>toString</code>, <code>getClass</code>, or <code>accessMode</code> are
 * prohibited. Additionally, {@code VarHandle} instances must not be passed as parameters to other
 * methods or exposed outside their declaring context.</p>
 *
 * <p>When a {@code VarHandle} is declared in one class (A) and accessed in another (B), both A and B
 * must be annotated with {@code @FastVarHandle}, or neither. It is strongly recommended to keep
 * such {@code VarHandle} fields package-private or private, avoiding {@code public} access.</p>
 *
 * @see VarHandle
 * @author Roj234
 * @since 2025/09/17 00:16
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface FastVarHandle { }