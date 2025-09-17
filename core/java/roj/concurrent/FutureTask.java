package roj.concurrent;

import org.jetbrains.annotations.NotNull;
import roj.compiler.api.Synchronizable;
import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;
import roj.util.Helpers;

import java.lang.invoke.VarHandle;
import java.util.concurrent.*;

/**
 * @author Roj234
 * @since 2020/8/19 1:01
 */
@Synchronizable
@FastVarHandle
public class FutureTask<T> implements RunnableFuture<T>, Cancellable {
	private static final int INITIAL = 0, RUNNING = 1, CANCELLING = 2, CANCELLED = 3, COMPLETED = 4, FAILED = 5;
	private static final VarHandle STATE = Handles.lookup().findVarHandle(FutureTask.class, "state", int.class);
	private volatile int state;

	private Thread executor;
	private volatile T result;

	private final Callable<T> callable;
	public FutureTask(Callable<T> c) { this.callable = c; }

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (STATE.compareAndSet(this, INITIAL, CANCELLED)) {
			synchronized (this) { notifyAll(); }
			return true;
		}

		if (mayInterruptIfRunning && STATE.compareAndSet(this, RUNNING, CANCELLING)) {
			var thread = executor;
			if (thread != null) thread.interrupt();
		}

		return state <= CANCELLED;
	}
	@Override
	public boolean isCancelled() { return state == CANCELLED; }

	@Override
	public final void run() {
		if (!STATE.compareAndSet(this, INITIAL, RUNNING)) return;

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