package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Alias to other type.
 * 例如签名兼容现有类型但能抛出异常的lambda
 * @author Roj234
 * @since 2025/06/03 09:21
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AliasOf {
	Class<?> value();
	String altValue() default "";
}
