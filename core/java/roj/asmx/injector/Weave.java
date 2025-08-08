package roj.asmx.injector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入类标记
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Weave {
	/**
	 * 目标类的全限定名（当编译时无法直接访问目标类时使用）
	 * <p>优先级低于{@link #target()}，当两者同时指定时以target为准</p>
	 */
	String value() default "";

	/**
	 * 目标类的Class对象
	 * <p>当编译时可访问目标类时优先使用此属性</p>
	 */
	Class<?> target() default Object.class;

	/**
	 * 是否将注入类实现的所有接口复制到目标类
	 * @see java.lang.Class#getInterfaces()
	 */
	boolean copyInterfaces() default true;

	/**
	 * 标志位（为未来扩展保留）
	 */
	int flags() default 0;
}