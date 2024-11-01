package roj.compiler.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/12/1 8:08
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface LavaPlugin {
	String name();
	String desc();
	String init() default "pluginInit";
}
