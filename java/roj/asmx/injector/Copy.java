package roj.asmx.injector;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段/方法复制
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Copy {
	/**
	 * 目标类中的名称（留空表示保持当前名称）
	 * 例如：当前字段名为"foo"，设置value="bar"后将在目标类中生成名为"bar"的字段
	 * 使用{@link Unique}时无效
	 * @return 目标字段/方法名称
	 */
	String value() default "";

	/**
	 * 配置标志位
	 * 仅{@link Inject#RUNTIME_MAP}有效
	 */
	@MagicConstant(intValues = Inject.RUNTIME_MAP)
	int flags() default 0;
}
