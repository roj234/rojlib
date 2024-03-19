package roj.asmx.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Nixim {
	String value(); // target
	boolean copyItf() default true;
	int flags() default 0; // no usage now
}