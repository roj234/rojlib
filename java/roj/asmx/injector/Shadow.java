package roj.asmx.injector;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 影子成员声明注解
 * @implNote 被标注字段/方法应在注入类或其父类中存在对应实现。<br>
 * 注入时若检测到以下情况将抛出{@link WeaveException}：<br>
 * • 目标成员不存在<br>
 * • final字段未使用{@link Final}注解标记
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Shadow {
	/**
	 * 目标字段名称（留空时需要预处理器支持）
	 */
	String value() default "";

	/**
	 * 字段所属类（默认使用{@link Weave#value()}指定的目标类）
	 * 例如: "java.util.ArrayList"
	 */
	String owner() default "";

	/**
	 * 配置标志位
	 * 仅{@link Inject#RUNTIME_MAP}有效
	 */
	@MagicConstant(intValues = Inject.RUNTIME_MAP)
	int flags() default 0;
}