package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * "基本泛型"
 *
 * @author Roj233
 * @since 2021/9/2 21:49
 */
@Retention(RetentionPolicy.CLASS)
//@Target(ElementType.TYPE_PARAMETER)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PrimitiveGeneric {
	/**
	 * Generic classes to be simulated
	 */
	String[] value();
}
