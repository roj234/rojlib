package roj.asmx.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2020/8/19 18:04
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Implements {
	Class<?>[] value();
}
