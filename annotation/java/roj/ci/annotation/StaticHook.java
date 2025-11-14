package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将value表示的方法映射到目标方法上
 * @author Roj234
 * @since 2025/10/17 0:04
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface StaticHook {
	String value();

	/**
	 * 使用Mapper在ASM阶段替换.
	 * 未实现.
	 * false时的行为是ASM重写value指定的方法作为代理.
	 */
	boolean useMapper() default false;
}
