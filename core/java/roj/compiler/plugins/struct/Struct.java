package roj.compiler.plugins.struct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/10/26 03:52
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Struct {
	boolean packed() default true;
}
