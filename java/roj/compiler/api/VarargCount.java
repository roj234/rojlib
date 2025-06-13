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
	int min() default 0;
	int max() default 65535;
	/**
	 * 必须是整数倍
	 * 例如2，那么数量必须是0，2，4等等
	 */
	int multiplyOf() default 1;
}