package roj.sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/9/1 0001 16:30
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Query {
	String value();
	String[] properties() default "";
	Returns returns() default Returns.DEFAULT;
	enum Returns {DEFAULT, AFFECTED_ID}
}
