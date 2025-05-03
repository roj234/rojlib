package roj.asmx.injector;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.cp.Constant;

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
	 * 目标方法名称
	 * @return '/'表示使用当前方法名，空字符串表示由{@code NiximHelper}决定
	 */
	String value() default "";
	/**
	 * 目标方法签名（留空表示与当前方法相同）
	 * 当{@link #replaceValue()}为空时，必须显式指定此参数
	 */
	String injectDesc() default "";

	/**
	 * 查找的常量类型，int包括所有比int小的类型
	 */
	@MagicConstant(intValues = {
			Constant.INT,    // 包含byte/short/char/int等整型
			Constant.FLOAT,  // 单精度浮点
			Constant.LONG,   // 长整型
			Constant.DOUBLE, // 双精度浮点
			Constant.CLASS,  // 类引用（Class对象）
			Constant.STRING  // 字符串常量
	})
	byte matchType();

	/**
	 * 需要匹配的常量值
	 * 例如 "42", "3.14F", "\"HelloWorld\""
	 * @see Constant#getEasyCompareValue()
	 */
	String matchValue();

	/**
	 * 替换后的常量值
	 * 空字符串表示替换为对当前注解方法的调用，此时：
	 * <ul>
	 *   <li>被注解方法必须返回与{@link #matchType}兼容的类型</li>
	 *   <li>目标方法签名用{@link #injectDesc}匹配</li>
	 *   <li>允许静态/非静态方法</li>
	 * </ul>
	 */
	String replaceValue() default "";

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