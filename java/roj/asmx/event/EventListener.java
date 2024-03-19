package roj.asmx.event;

/**
 * @author Roj234
 * @since 2024/3/21 0021 12:48
 */
interface EventListener {
	void invoke(Event event);
	// 默认是给frozen event准备的，反正不override就没法移除了！
	default boolean isFor(Object handler, String methodName, String methodDesc) { return false; }
}