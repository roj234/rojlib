package roj.compiler.api;

import roj.ci.annotation.InRepo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/12/1 8:08
 */
@InRepo
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CompilerPlugin {
	String name();
	String desc();
	boolean instance() default false;
	String init() default "pluginInit";
}
