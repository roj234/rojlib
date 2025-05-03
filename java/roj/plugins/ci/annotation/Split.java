package roj.plugins.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/8/28 4:00
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
public @interface Split {
	public String value();
}
