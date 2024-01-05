package roj.concurrent.task;

import org.jetbrains.annotations.ApiStatus;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2020/8/19 1:01
 */
public class AsyncTask<T> implements Future<T>, ITask {
	private static final int INITIAL = 0, RUNNING = 1, COMPLETED = 2, FAILED = 3, CANCELLED = 4;
	private static final long u_stateOffset = ReflectionUtils.fieldOffset(AsyncTask.class, "state");
	private volatile int state;

	private volatile T out;

	protected Callable<T> supplier;

	public static AsyncTask<Void> fromRunnable(Runnable runnable) {
		return new AsyncTask<>(() -> {
			runnable.run();
			return null;
		});
	}

	public AsyncTask(Callable<T> c) { this.supplier = c; }
	protected AsyncTask() {}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (u.compareAndSwapInt(this, u_stateOffset, INITIAL, RUNNING)) {
			state = CANCELLED;
			synchronized (this) { notifyAll(); }
			return true;
		}
		return isCancelled();
	}
	@Override
	public boolean isCancelled() { return state == CANCELLED; }

	@Override
	public final void execute() {
		if (!u.compareAndSwapInt(this, u_stateOffset, INITIAL, RUNNING)) return;

		try {
			out = invoke();
			state = COMPLETED;
		} catch (Throwable e) {
			out = Helpers.cast(e);
			state = FAILED;
		}

		synchronized (this) { notifyAll(); }
	}

	@ApiStatus.OverrideOnly
	protected T invoke() throws Exception { return supplier.call(); }
	protected final boolean setDone(T result) {
		if (!u.compareAndSwapInt(this, u_stateOffset, INITIAL, RUNNING)) return false;
		out = result;
		state = COMPLETED;
		return true;
	}
	protected final boolean setFailed(Throwable e) {
		if (!u.compareAndSwapInt(this, u_stateOffset, INITIAL, RUNNING)) return false;
		out = Helpers.cast(e);
		state = FAILED;
		return true;
	}

	public boolean isDone() { return state > RUNNING; }

	@Override
	public T get() throws InterruptedException, ExecutionException {
		synchronized (this) { while (!isDone()) wait(); }
		return getOrThrow();
	}

	@Override
	public T get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
		synchronized (this) { if (!isDone()) wait(unit.toMillis(timeout)); }
		if (!isDone()) throw new TimeoutException();
		return getOrThrow();
	}

	private T getOrThrow() throws ExecutionException {
		switch (state) {
			default: throw new IllegalStateException("unknown state"+state);
			case CANCELLED: throw new CancellationException();
			case FAILED: throw new ExecutionException((Throwable) out);
			case COMPLETED: return out;
		}
	}
}