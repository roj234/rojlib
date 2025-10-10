package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 未实现
 * @author Roj234
 * @since 2025/10/17 0:04
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface RedirectTo {
	String value();
}
