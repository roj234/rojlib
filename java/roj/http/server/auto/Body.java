package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/2/5 0005 11:34
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Body {
	From value();
	boolean nonnull() default false;
}