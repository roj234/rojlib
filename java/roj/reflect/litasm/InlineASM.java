package roj.reflect.litasm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/10/12 0012 17:38
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InlineASM {
	String[] value();
	String[] platform() default {"win64","linux64"};
}
