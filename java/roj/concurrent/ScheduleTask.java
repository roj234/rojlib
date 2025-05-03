package roj.concurrent;

/**
 * @author Roj234
 * @since 2024/3/6 2:27
 */
public interface ScheduleTask {
	Task getTask();

	/**
	 * 当任务过期时，返回 {@code true}.
	 * 当这个方法返回 {@code true} 时，任务仅提交给了执行者，其是否会执行，何时执行，执行结果如何都是不确定的。
	 *
	 * @return 任务是否过期
	 */
	boolean isExpired();
	/**
	 * 当任务在过期前被取消了，返回 {@code true}.
	 *
	 * @return 任务是否取消
	 */
	boolean isCancelled();
	/**
	 * 尝试取消任务.
	 * 将调用getTask().cancel()一次，此外如果任务还未过期，那么它将不会过期.
	 * @return 如果取消了未过期的任务，那么返回true
	 */
	boolean cancel();
}