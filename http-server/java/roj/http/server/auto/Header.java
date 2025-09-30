package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects an HTTP header value into a method parameter of the route handler.
 * The header name is specified by the {@link #value()} attribute.
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @POST
 * public void inference(@Header("Authorization") String token) { ... }
 * }</pre>
 *
 * <p>Supports string types; other types may require custom conversion.</p>
 *
 * @author Roj234
 * @since 2025/4/26 3:04
 * @see RequestParam
 * @see QueryParam
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Header {
	/**
	 * The name of the HTTP header to inject.
	 *
	 * @return the header name
	 */
	String value();
}