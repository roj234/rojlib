package roj.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为事件监听器。
 * <p>
 * 该注解应用于方法，用于订阅特定事件。方法签名必须为：
 * <pre>public [可选 static] void methodName(EventType event)</pre>
 * 其中EventType必须继承{@link Event}，返回值必须为void。
 * </p>
 * <p>
 * 支持实例方法和静态方法。{@link Priority 优先级}决定执行顺序（{@code HIGHEST}最先），{@code receiveCancelled}决定是否在事件已取消时仍执行。
 * </p>
 * <p>
 * <strong>使用示例：</strong>
 * <pre>
 * &#64;Subscribe(priority = Priority.HIGH, receiveCancelled = true)
 * public void onPlayerMove(PlayerMoveEvent event) {
 *     if (event.isCanceled()) {
 *         // 仍处理已取消事件，例如日志
 *         return;
 *     }
 *     // 正常处理
 * }
 * </pre>
 *
 * @author Roj234
 * @since 2024/3/21
 * @see EventBus#register(Object)
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Subscribe {
	Priority priority() default Priority.NORMAL;
	boolean receiveCancelled() default false;
}