package roj.kscript.func.gen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 该方法是一个Jvav方法
 *
 * @author solo6975
 * @since 2020/10/17 18:13
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvavMethod {
	String name() default "";
}
