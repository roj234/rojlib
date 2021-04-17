package roj.asm.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 重映射方法
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Inject {
	int FLAG_OPTIONAL = 1;
	int FLAG_MODIFIABLE_PARAMETER = 2;

	/**
	 * @return ‘/’代表使用方法名 ''代表由NiximHelper实现决定
	 */
	String value() default "";

	At at() default At.OLD_SUPER_INJECT;

	int flags() default 0;

	String[] param() default {};

	/**
	 * 目标方法的参数
	 */
	String desc() default "";

	enum At {
		HEAD, MIDDLE, TAIL, REPLACE, OLD_SUPER_INJECT, REMOVE, INVOKE, LDC
	}
}
