package roj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 一个普通的注释，没有任何处理
 * 所以你可以在ClassNode#toString里看到它，仅此而已
 * @author Roj234
 * @since 2025/09/14 06:45
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface Comment {
	String value();
}
