package roj.compiler.api;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制方法可变参数的数量
 * @author Roj234
 * @since 2025/4/30 17:43
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface VarargCount {
	int min();
	int max();
}