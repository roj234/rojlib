package roj.concurrent.task;

import org.jetbrains.annotations.ApiStatus;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

/**
 * @author Roj234
 * @since 2020/8/19 1:01
 */
public class AsyncTask<T> implements Future<T>, ITask {
	protected volatile T out = Helpers.cast(this);
	protected boolean canceled, executing;
	protected ExecutionException exception;
	protected Callable<T> supplier;

	public static AsyncTask<Void> fromVoid(Runnable runnable) {
		return new AsyncTask<>(() -> {
			runnable.run();
			return null;
		});
	}

	public AsyncTask(Callable<T> supplier) {
		this.supplier = supplier;
	}

	@SuppressWarnings("unchecked")
	protected AsyncTask() {
		out = (T) this;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (executing) {
			return false;
		} else {
			canceled = true;
			out = null;
			synchronized (this) {
				notifyAll();
			}
			return true;
		}
	}

	@Override
	public boolean isCancelled() {
		return canceled;
	}

	@Override
	public void execute() {
		executing = true;
		try {
			this.out = invoke();
		} catch (Throwable e) {
			exception = new ExecutionException(e);
		}
		executing = false;

		synchronized (this) {
			notifyAll();
		}
	}

	@ApiStatus.OverrideOnly
	protected T invoke() throws Exception {
		return supplier.call();
	}

	public boolean isDone() {
		return canceled || out != this || exception != null;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {
		if (this.isCancelled()) {
			throw new CancellationException();
		}

		if (this.exception != null) throw exception;
		synchronized (this) {
			if (out == this) {
				this.wait();
			}
		}

		if (this.isCancelled()) {
			throw new CancellationException();
		}

		if (this.exception != null) throw exception;

		return out;
	}

	@Override
	public T get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
		if (this.isCancelled()) {
			throw new CancellationException();
		}

		if (this.exception != null) throw exception;
		synchronized (this) {
			if (out == this) {
				this.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
			}
		}

		if (this.isCancelled()) {
			throw new CancellationException();
		}

		synchronized (this) {
			if (this.exception != null) throw exception;
			if (out == this) {
				throw new TimeoutException();
			}
		}

		return out;
	}
}
