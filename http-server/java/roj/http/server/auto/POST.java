package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a POST route handler in the OKRouter framework.
 * The route path defaults to the method name with "__" replaced by "/".
 * Equivalent to {@link Route} with POST method implied.
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @POST("/users/create")
 * public void createUser(@Body User user) { ... }
 * }</pre>
 *
 * <p>Use {@link Accepts} and {@link Route} to allow multiple methods if needed.</p>
 *
 * @author Roj234
 * @since 2024/7/8 4:54
 * @see Route
 * @see Accepts
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface POST {
	String value() default "";
}