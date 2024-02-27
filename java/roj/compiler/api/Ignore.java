package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 忽略一些错误，以错误码匹配
 * 有些错误码是嵌套的，你只能忽略第一层
 * 如果想更精细的处理，可以覆盖{@link roj.compiler.context.GlobalContext#listener}
 * 即使忽略了错误，生成的代码也不一定可以工作
 * @author Roj234
 * @since 2024/6/7 0007 3:00
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface Ignore {
	String[] value();
	/**
	 * 显示错误码
	 */
	boolean show() default false;
}