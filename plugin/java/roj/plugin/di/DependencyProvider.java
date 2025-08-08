package roj.plugin.di;

import roj.ci.annotation.InRepo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiConsumer;

/**
 * 标记依赖提供方法，用于创建可注入的依赖实例。
 *
 * <p><b>方法签名要求：</b>
 * <pre>
 * // 基础形式
 * {@code public static [Type] methodName([可选参数])}
 *
 * // 支持的可选参数：
 *   - {@link DIContext} : 注入点上下文
 *   - {@code @Nullable String qualifier} : 接收{@link Autowired#qualifier()}值，仅允许{@link #allowQualifier()}时允许存在
 * </pre>
 *
 * 如果当前类中注册了相同类型的{@link DIContext#registerUnloadCallback(Class, BiConsumer)}那么它会记录通过{@link Autowired}创建的每一个实例，并在插件卸载时调用
 *
 * @author Roj234
 * @since 2025/06/20 19:57
 */
@InRepo
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface DependencyProvider {
	/**
	 * 提供方法优先级（默认0），数值越大优先级越高
	 */
	int priority() default 0;

	boolean allowQualifier() default false;
}
