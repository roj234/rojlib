package roj.optimizer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.VarHandle;

/**
 * Marks a class or interface for optimized {@link VarHandle} access.
 *
 * <p>Restrictions for FastVarHandle</p>
 * <table>
 *   <tr>
 *     <th width=1>Kind</th>
 *     <th>Restriction</th>
 *   </tr>
 *   <tr>
 *     <td>保存</td>
 *     <td>The {@code VarHandle} instances must be directly loaded from / saved to fields.</td>
 *   </tr>
 *   <tr>
 *     <td>数组</td>
 *     <td>For array-based {@code VarHandle}s, the field name must end with {@code $ARRAY}.</td>
 *   </tr>
 *   <tr>
 *     <td>静态字段</td>
 *     <td>For static field-based {@code VarHandle}s, the field name must end with {@code $STATIC}.</td>
 *   </tr>
 *   <tr>
 *     <td>方法调用</td>
 *     <td>Only a restricted set of {@code VarHandle} methods are permitted: <code>get</code>, <code>set</code>, <code>compareAndSet</code> (and other atomic variants like <code>compareAndExchange</code>), and similar direct accessors. Reflective or informational methods such as <code>toString</code>, <code>getClass</code>, or <code>accessMode</code> are prohibited.</td>
 *   </tr>
 *   <tr>
 *     <td>参数传递</td>
 *     <td>{@code VarHandle} instances can only be passed as the <em>last</em> parameter of a method. It is important to note that when using the signature with (..., {@code VarHandle}), it is only valid if the parameter is immediately saved to a variable. Otherwise, the signature must use (..., {@code VarHandle}, int padding) instead. In such cases, the {@code VarHandle} must target an instance field or array (not a static field).</td>
 *   </tr>
 *   <tr>
 *     <td>字段引用</td>
 *     <td>When a {@code VarHandle} is declared in one class (A) and accessed in another (B), both A and B must be annotated with {@code @FastVarHandle}, or neither.</td>
 *   </tr>
 * </table>
 * It is strongly recommended to keep such {@code VarHandle} fields package-private or private, avoiding {@code public} access.</p>
 *
 * @see VarHandle
 * @author Roj234
 * @since 2025/09/17 00:16
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface FastVarHandle { }