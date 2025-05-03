package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 尾调用递归优化.
 * 对于不可继承的方法（含有任意修饰符：static，final，private），它是自动启用的
 * 对于其它方法，你可以使用这个注解来启用优化
 * 你也可以使value=false来禁用优化
 * @author Roj234
 * @since 2024/8/8 19:19
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Tailrec {
	boolean value() default true;
}