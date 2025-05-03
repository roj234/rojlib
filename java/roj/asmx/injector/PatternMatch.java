
package roj.asmx.injector;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字节码模式匹配&替换
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface PatternMatch {
	/**
	 * 目标方法名称
	 * @return '/'表示使用当前方法名，空字符串表示由{@code NiximHelper}决定
	 */
	String value() default "";

	/**
	 * 要匹配的代码段在注入类中的名称（pattern）
	 * @apiNote 被注解方法将作为替换目标（replacement）
	 */
	String matcher();

	/**
	 * 配置标志位
	 * 与{@link Inject#flags()}相同
	 */
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;

	/**
	 * 替换哪几个匹配
	 * @return 空数组表示替换所有匹配，[0,2]表示替换第1和第3个匹配
	 */
	int[] occurrences() default {};
}