package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a query parameter from the URL into a method parameter.
 * The parameter name is specified by {@link #value()}; defaults to the parameter name if empty.
 * Supports optional parameters via {@link #orDefault()}.
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @Route("/search")
 * public Results search(@QueryParam("q") String query, @QueryParam(value = "page", orDefault = "1") int page) { ... }
 * }</pre>
 *
 * <p>Empty {@link #orDefault()} means required; non-empty provides a default value if absent.</p>
 *
 * @author Roj234
 * @since 2025/4/26 3:04
 * @see RequestParam
 * @see Body
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface QueryParam {
	/**
	 * The query parameter name. If empty, uses the parameter's name.
	 *
	 * @return the parameter name
	 */
	String value() default "";
	/**
	 * Default value if the parameter is absent.
	 * Once declared in annotation, even if it is "", will provide the fallback value.
	 * a.k.a. @QueryParam(value = "filter", orDefault = "") != @QueryParam(value = "filter")
	 *
	 * @return the default value string
	 */
	String orDefault() default "";
}