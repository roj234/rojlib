package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 预编译
 *
 * @author Roj233
 * @since 2021/9/2 21:52
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface PreCompile {
	String[] fields() default {};

	/**
	 * Is primitive type, its array or String
	 */
	boolean simple() default true;
}
