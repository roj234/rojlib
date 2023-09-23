package roj.asm.nixim;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Inject {
	/**
	 * @return ‘/’代表使用方法名 ''代表由NiximHelper实现决定
	 */
	String value() default "";

	enum At { HEAD, TAIL, REPLACE, OLD_SUPER_INJECT, REMOVE }
	At at() default At.OLD_SUPER_INJECT;

	int OPTIONAL = 1;
	int RUNTIME_MAP = 2;
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;

	String injectDesc() default "";
}
