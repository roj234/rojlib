package roj.concurrent;

import org.jetbrains.annotations.ApiStatus;
import roj.ci.annotation.AliasOf;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author Roj234
 * @since 2020/11/30 23:07
 */
@FunctionalInterface
public interface Executor extends java.util.concurrent.Executor {
	@Override void execute(Runnable task);
	/**
	 * 请注意，如果抛出了异常，那么是由线程池的异常处理器处理（默认是打印日志） <br>
	 * 除非你确定不会真的抛出异常，否则不应该调用此方法，稍微做点错误处理吧……
	 * @see AliasOf Task是Runnable，而且永远是
	 */
	@ApiStatus.Obsolete
	default void executeUnsafe(Task task) {execute((Runnable) task);}

	default <T> Future<T> submit(Callable<T> task) {
		var wrapper = new FutureTask<>(Objects.requireNonNull(task));
		execute(wrapper);
		return wrapper;
	}

	default TaskGroup newGroup() {return newGroup(false);}
	default TaskGroup newGroup(boolean helpRunTask) {return new TaskGroup(this, helpRunTask);}
}