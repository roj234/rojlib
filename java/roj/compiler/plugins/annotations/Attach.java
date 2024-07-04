package roj.compiler.plugins.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将这个类中所有的静态函数附加到它们第一个参数指定的类上
 * @author Roj234
 * @since 2024/6/10 0010 3:24
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Attach {
	String value() default "";
}