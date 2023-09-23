package roj.asm.nixim;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.cst.Constant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 覆盖常量
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface OverwriteConstant {
	/**
	 * @return ‘/’代表使用方法名 ''代表由NiximHelper实现决定
	 */
	String value() default "";
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;

	@MagicConstant(intValues = {Constant.INT, Constant.FLOAT, Constant.LONG, Constant.DOUBLE, Constant.CLASS, Constant.STRING})
	byte matchType();
	String matchValue();

	String injectDesc() default "";
	/**
	 * 留空则替换成方法调用(然而这并不提倡)
	 * 这时入参为空，返回matchType，用injectDesc匹配
	 */
	String replaceValue() default "";

	int[] occurrences() default {};
}
