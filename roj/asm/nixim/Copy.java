package roj.asm.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Copy {
	boolean map() default false;

	boolean unique() default false;

	String value() default "";

	String staticInitializer() default "";

	boolean targetIsFinal() default false;
}
