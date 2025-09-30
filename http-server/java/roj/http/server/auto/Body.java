package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the request body into a method parameter of the route handler.
 * Supports deserialization from JSON、MsgPack or WebForm (a.k.a. {@code MultiMap<String, String>}).
 * When used on a method parameter, it extracts the body content.
 * Additional attributes control the deserialization behavior.
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @POST("/user")
 * public Content createUser(@Body User user) { ... }
 * }</pre>
 *
 * <p>By default, deserialization source is (and current only support) request body, router will automatically select appropriate deserializer via {@code Content-Type} request header.
 * This annotation conflicts with {@link Route#deserializeFrom()} and {@link RequestParam}.</p>
 *
 * @author Roj234
 * @since 2023/2/5 11:34
 * @see RequestParam
 * @see QueryParam
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Body {
	/**
	 * Use this to use integral key rather than String key in map-like structure to reduce payload size.
	 * Only support MsgPack format.
	 * Not implemented.
	 */
	boolean schemaless() default false;
}