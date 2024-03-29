package roj.net.http.srv.autohandled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/2/5 0005 11:34
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Field {
	String value() default "";
	From from() default From.INHERIT;
	boolean nonnull() default false;
}
