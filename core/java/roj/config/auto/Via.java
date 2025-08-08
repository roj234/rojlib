package roj.config.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通过指定的set和get方法来修改该字段
 * 可以只填一个
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Via {
	String get() default "";
	String set() default "";
}