package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers an interceptor for routes, either globally or locally.
 * Can be applied to methods (for specific routes) or classes (for all routes in the class).
 * Interceptors are executed after receiving request headers but before the full request or route handler.
 *
 * <p><strong>Registering:</strong> Apply to non-route methods to register them as interceptors.
 * Returns non-null value to short-circuit the chain.</p>
 *
 * <p><strong>CORS example:</strong></p>
 * <pre>{@code
 * @Interceptor("cors")  // Registers the method as a named interceptor
 * public Content cors(Request req) {
 *     return req.checkOrigin(null);  // null = no CORS policy (allow any origin)
 * }
 *
 * @Route("/api/data")
 * @Interceptor("cors")  // Applies the interceptor to this route
 * public String getData(@Body Data body) { ... }
 * }</pre>
 *
 * <p><strong>Class-level example:</strong></p>
 * <pre>{@code
 * @Interceptor("checkLogin")
 * public class UserCenter { ... }
 * }</pre>
 *
 * Interceptors are applied in declaration order (class first, then method).
 * Global interceptors are available across the {@link OKRouter} instance; local ones are scoped to the registered object.
 *
 * @author Roj234
 * @since 2023/2/5 11:35
 * @see Route
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Interceptor {
	/**
	 * Names of interceptors to apply or register.
	 * Length must be 1 in register context.
	 *
	 * @return the interceptor identifiers
	 */
	String[] value() default "";
	/**
	 * If {@code true}, the interceptor is global and available in registered OKRouter instance.
	 * Otherwise, it is scoped to the registered object.
	 * Only meanful in register context.
	 *
	 * @return whether the interceptor is global
	 */
	boolean global() default false;
}