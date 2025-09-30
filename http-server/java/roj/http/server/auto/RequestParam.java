package roj.http.server.auto;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a request parameter into a method parameter.
 * Supports various sources like query params, form data, etc., via {@link #source()}.
 * Defaults to "PARAM" source (request path params).
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @POST("/form/:name/submit")
 * public void submit(@RequestParam String name) { ... }
 * }</pre>
 *
 * <p>Use {@link #source()} to specify "GET" (query), "POST" (body/form), "COOKIE", etc.</p>
 * <p>This annotation may conflict with {@link Body}.</p>
 *
 * @author Roj234
 * @since 2025/4/26 3:04
 * @see QueryParam
 * @see Header
 * @see Body
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
	/**
	 * The parameter name. If empty, uses the parameter's name.
	 *
	 * @return the parameter name
	 */
	String value() default "";
	/**
	 * The source of the parameter: "REQUEST" (try POST, then GET), "GET" (query), "POST" (body/form),
	 * "COOKIE", "HEAD" (headers ref {@link Header}), "BODY" (ref {@link Body}), or "PARAM" (path params).
	 *
	 * @return the source type
	 */
	@MagicConstant(stringValues = {"REQUEST", "GET", "POST", "COOKIE", "HEAD", "BODY", "PARAM"})
	String source() default "PARAM";
}