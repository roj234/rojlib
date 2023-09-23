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
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Shadow {
	/**
	 * 如果留空，需要配FMD使用
	 *
	 * @return target field name
	 */
	String value() default "";

	/**
	 * @return field owner, default: @Nixim.target
	 */
	String owner() default "";
}
