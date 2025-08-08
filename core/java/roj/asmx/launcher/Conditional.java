package roj.asmx.launcher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/4/4 20:39
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Conditional {
	/**
	 * 当运行时某个资源无法找到时激活
	 * 例如 java/awt/Color.class
	 * <p>优先级低于{@link #target()}，当两者同时指定时以target为准</p>
	 */
	String value() default "";
	/**
	 * 目标类的Class对象
	 * <p>当基于类而不是资源时优先使用此属性</p>
	 */
	Class<?> target() default Object.class;

	/**
	 * 仅类上有效，激活时移除这些接口
	 */
	Class<?>[] itf() default {};
	/**
	 * 仅方法上有效，删除该方法，或将其实现替换为空
	 * 类上REMOVE不起作用，而DUMMY会替换整个类的实现为空
	 */
	Action action() default Action.REMOVE;
	enum Action {REMOVE, DUMMY}
}
