package roj.event;

/**
 * 事件监听器的执行优先级枚举。
 * <p>
 * 优先级决定监听器在事件分发时的执行顺序：{@code HIGHEST}最先执行，{@code LOWEST}最后执行。
 * 同一优先级的监听器按注册顺序执行。
 * </p>
 * <p>
 * 优先级通过{@link Subscribe#priority()}指定，默认{@code NORMAL}。
 *
 * @author Roj234
 * @since 2024/3/21
 */
public enum Priority {
	HIGHEST, HIGH, NORMAL, LOW, LOWEST;

	static final byte MASK = 7;
	static final Priority[] PRIORITIES = values();
}