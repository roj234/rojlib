package roj.compiler.plugins.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 以指定的value和step为它覆盖的每个字段赋整数值
 * @author Roj234
 * @since 2024/4/12 19:19
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface AutoIncrement {
	int value() default 0;
	int step() default 1;
}