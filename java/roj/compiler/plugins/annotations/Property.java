package roj.compiler.plugins.annotations;

import roj.compiler.api.Stackable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动属性.
 * getter和setter通常自动从value生成，但是不会为布尔值生成isXXX，依然是getXXX
 * 你也可以手动输入名称
 * @author Roj234
 * @since 2024/6/24 22:59
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Stackable
public @interface Property {
	String value();
	String getter() default "";
	String setter() default "";
}