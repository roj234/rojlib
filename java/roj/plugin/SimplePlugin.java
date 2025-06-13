package roj.plugin;

import roj.plugins.ci.annotation.InRepo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/12/26 12:43
 */
@InRepo
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SimplePlugin {
	String id();
	String version() default "";
	String desc() default "";
	String[] depend() default {};
	String[] loadAfter() default {};
	boolean inheritConfig() default false;
}