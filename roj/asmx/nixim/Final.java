package roj.asmx.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/10/9 19:27
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD})
public @interface Final {
	boolean setFinal() default false;
}
