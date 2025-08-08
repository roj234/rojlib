package roj.concurrent;

import org.jetbrains.annotations.NotNull;
import roj.compiler.api.Synchronizable;
import roj.reflect.Unaligned;
import roj.util.Helpers;

import java.util.concurrent.*;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2020/8/19 1:01
 */
@Synchronizable
public class FutureTask<T> implements RunnableFuture<T>, Cancellable {
	private static final int INITIAL = 0, RUNNING = 1, CANCELLING = 2, CANCELLED = 3, COMPLETED = 4, FAILED = 5;
	private static final long STATE = Unaligned.fieldOffset(FutureTask.class, "state");
	private volatile int state;

	private Thread executor;
	private volatile T result;

	private final Callable<T> callable;
	public FutureTask(Callable<T> c) { this.callable = c; }

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (U.compareAndSetInt(this, STATE, INITIAL, CANCELLED)) {
			synchronized (this) { notifyAll(); }
			return true;
		}

		if (mayInterruptIfRunning && U.compareAndSetInt(this, STATE, RUNNING, CANCELLING)) {
			var thread = executor;
			if (thread != null) thread.interrupt();
		}

		return state <= CANCELLED;
	}
	@Override
	public boolean isCancelled() { return state == CANCELLED; }

	@Override
	public final void run() {
		if (!U.compareAndSetInt(this, STATE, INITIAL, RUNNING)) return;

		try {
			executor = Thread.currentThread();
			result = callable.call();
			state = COMPLETED;
		} catch (Throwable e) {
			result = Helpers.cast(e);
			state = FAILED;
		} finally {
			executor = null;
		}

		synchronized (this) { notifyAll(); }
	}

	public boolean isDone() { return state > CANCELLING; }

	@Override
	public T get() throws InterruptedException, ExecutionException {
		synchronized (this) { while (!isDone()) wait(); }
		return getOrThrow();
	}

	@Override
	public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
		synchronized (this) { if (!isDone()) wait(unit.toMillis(timeout)); }
		if (!isDone()) throw new TimeoutException();
		return getOrThrow();
	}

	private T getOrThrow() throws ExecutionException {
		return switch (state) {
			default -> throw new IllegalStateException("unknown state "+state);
			case CANCELLED -> throw new CancellationException();
			case FAILED -> throw new ExecutionException((Throwable) result);
			case COMPLETED -> result;
		};
	}
}