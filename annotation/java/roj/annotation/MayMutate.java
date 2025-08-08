package roj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 你决定将可变对象的控制权转交给此函数，除非了解内部实现，否则不应该再使用这个参数
 * @author Roj234
 * @since 2024/6/24 8:35
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface MayMutate {
	String value() default "";
}