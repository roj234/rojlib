package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防止静态分析器意外删除这个类.
 * 如果by不填，那么假设和所处类相同
 * @author Roj234
 * @since 2024/1/6 2:19
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
public @interface IndirectReference {
	Class<?>[] by() default {};
}