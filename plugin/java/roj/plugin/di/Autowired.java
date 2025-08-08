package roj.plugin.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要依赖注入的字段。框架会在宿主类的静态初始化阶段自动注入依赖实例。
 *
 * <p><b>仅支持静态字段注入</b>
 *
 * <p><b>清理机制：</b>
 * 当关联插件卸载时，框架会自动调用匹配的{@link DIContext#registerUnloadCallback}方法进行资源清理
 *
 * @author Roj234
 * @since 2025/06/20 19:55
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Autowired {
	/**
	 * 依赖标识符(或称参数)，用于区分同类型的多个依赖实现。
	 * 需要依赖提供方支持该属性，否则将在预处理时抛出异常
	 */
	String qualifier() default "";
}
