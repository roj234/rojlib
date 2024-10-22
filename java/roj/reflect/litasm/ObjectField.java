package roj.reflect.litasm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数类型：指针
 * @author Roj234
 * @since 2024/10/22 8:04
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ObjectField {
	String value();
}
