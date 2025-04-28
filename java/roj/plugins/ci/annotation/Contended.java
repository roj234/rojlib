package jdk.internal.vm.annotation;

import roj.plugins.ci.annotation.CompileOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@CompileOnly
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface Contended {
	/**
	 * 组
	 */
	String value() default "";
}
