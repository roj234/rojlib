
package roj.asmx.nixim;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 重映射方法
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface InvokeRedirect {
	/**
	 * @return ‘/’代表使用方法名 ''代表由NiximHelper实现决定
	 */
	String value() default "";
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;

	/**
	 * (匹配用)方法参数
	 * 方法的形参是用来匹配被映射的方法，而该参数
	 */
	String injectDesc();

	String matcher();

	int[] occurrences() default {};
}
