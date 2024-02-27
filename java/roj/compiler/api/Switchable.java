package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 让这个类的实例可以被switch
 * @author Roj234
 * @since 2022/10/23 0023 13:27
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Switchable {
	/**
	 * 使用identityHashcode还是Object#hashCode
	 */
	boolean identity() default true;
	/**
	 * 像enum一样建议同类中的static字段
	 */
	boolean suggest() default false;
}