package roj.asmx.injector;

import roj.asm.Attributed;
import roj.asm.annotation.AList;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 动态行为开关
 * @apiNote 被标注元素的注入行为将由{@code ClassWeaver}在运行时动态决定
 * @see CodeWeaver#shouldApply(String, Attributed, AList)
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
public @interface Dynamic {
	/**
	 * 传递给{@code shouldApply}的最后一个参数
	 * @return 动态配置参数数组
	 */
	String[] value();
}
