package roj.event;

import roj.ci.annotation.IndirectReference;

/**
 * 事件系统的基类，表示一个可分发的事件。<br>
 * 所有事件类应继承此抽象类，并可选地添加 {@link Cancellable} 注解以支持取消机制。<br>
 * <p>
 * 默认实现：
 * <ul>
 *     <li>事件不可取消：{@link #cancel()} 抛出异常。</li>
 *     <li>{@link #isCancellable()} 和 {@link #isCanceled()} 返回 {@code false}。</li>
 *     <li>泛型事件： {@link #getGenericValueType()} 抛出异常。</li>
 * </ul>
 * </p>
 * <p>
 * 子类可以通过重写这些方法来自定义行为。对于泛型事件（如 {@code class MyEvent<T> extends Event}），必须实现 {@link #getGenericType()} 或 {@link #getGenericValueType()} 以确保类型安全匹配。
 * </p>
 *
 * @author Roj234
 * @since 2024/3/21
 * @see Cancellable
 * @see EventBus#post(Event)
 */
public abstract class Event {
	/**
	 * 尝试取消此事件。<br>
	 * 默认实现抛出 {@link UnsupportedOperationException}，因为基类事件不可取消。<br>
	 * 子类若添加了 {@link Cancellable} 注解，将通过字节码注入覆盖此方法，实现实际的取消逻辑（设置内部取消标志）。
	 *
	 * @throws UnsupportedOperationException 如果事件不可取消
	 */
	public void cancel() { throw new UnsupportedOperationException(getClass().getName()+"不可取消"); }
	/**
	 * 检查此事件是否可取消。<br>
	 * 默认返回 {@code false}。<br>
	 * 若类上标注了 {@link Cancellable}，将通过字节码注入返回 {@code true}。
	 *
	 * @return {@code true} 如果事件支持取消，否则 {@code false}
	 */
	public boolean isCancellable() { return false; }
	/**
	 * 检查此事件是否已被取消。<br>
	 * 默认返回 {@code false}。<br>
	 * 对于可取消事件，此方法返回内部取消标志的状态。
	 *
	 * @return {@code true} 如果事件已被取消，否则 {@code false}
	 */
	public boolean isCanceled() { return false; }

	/**
	 * 获取此事件的泛型类型描述（ASM内部格式，如 "Ljava/lang/String;"）。<br>
	 * 用于泛型事件在运行时的类型匹配。<br>
	 * 默认返回基于 {@link #getGenericValueType()} 的描述。<br>
	 * 子类应重写以支持泛型参数。
	 *
	 * @return 泛型类型的ASM描述字符串
	 */
	@IndirectReference
	public String getGenericType() { return "L"+getGenericValueType().getName().replace('.', '/')+";"; }
	/**
	 * 获取此事件的泛型值类型（Class）。<br>
	 * 默认实现抛出 {@link UnsupportedOperationException}，因为基类不具有泛型。<br>
	 * 用于泛型事件（如事件携带值的类型）。子类应重写以返回实际的泛型参数类型。
	 *
	 * @return 泛型值类型
	 */
	protected Class<?> getGenericValueType() { throw new UnsupportedOperationException("具有泛型参数的事件类"+getClass().getName()+"必须覆盖getGenericType或getGenericValueType方法"); }
}