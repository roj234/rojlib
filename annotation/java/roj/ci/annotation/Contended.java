package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.TYPE})
@AliasOf(value = Object.class, altValue = "jdk.internal.vm.annotation.Contended")
public @interface Contended {
	/**
	 * 组
	 */
	String value() default "";
}
