package roj.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个事件类为可取消的（Cancellable）。<br>
 * 当事件类上添加此注解时，{@link EventTransformer} 将自动注入以下方法：
 * <ul>
 *     <li>{@link Event#cancel()}：取消事件（设置取消标志）。</li>
 *     <li>{@link Event#isCanceled()}：检查事件是否已被取消。</li>
 *     <li>{@link Event#isCancellable()}：返回 {@code true}，表示支持取消。</li>
 * </ul>
 *
 * @author Roj234
 * @since 2024/3/21
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Cancellable {}