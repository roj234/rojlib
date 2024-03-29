package roj.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:43
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SimplePlugin {
	String id();
	String version() default "1";
	String desc() default "";
	//String[] depend() default {};
	//String[] loadAfter() default {};
	//String[] loadBefore() default {};
}
