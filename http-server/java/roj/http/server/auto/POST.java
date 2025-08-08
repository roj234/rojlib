package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/7/8 4:54
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface POST {
	String value() default "";
}