package roj.asmx.launcher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/5/1 19:48
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Autoload {
	Target value();
	String group() default "";
	int priority() default 0;
	int intrinsic() default -1;

	enum Target {NIXIM, TRANSFORMER, INIT}
}