package roj.concurrent;

import org.jetbrains.annotations.NotNull;
import roj.compiler.api.Synchronizable;
import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.lang.invoke.VarHandle;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2022/10/8 0:08
 */
@Synchronizable
@FastVarHandle
final class PromiseImpl<T> implements Promise<T>, Runnable, Cancellable, Promise.Callback {
	PromiseImpl() {}
	PromiseImpl(Executor executor) {this.executor = executor;}

	private static final VarHandle STATE = Handles.lookup().findVarHandle(PromiseImpl.class, "state", int.class);
	static final int
		TASK_COMPLETE = 1, TASK_SUCCESS = 2,
		WAIT = 4, CALLBACK = 8,
		INVOKE_HANDLER = 16;
	volatile int state;

	private BiConsumer<?, Callback> resolveHandler;
	private Function<Throwable, ?> rejectHandler;

	Object result;

	private PromiseImpl<?> next;
	private Executor executor, rejectExecutor;

	@Override
	public Promise<Object> then(BiConsumer<T, Callback> fn, Function<Throwable, ?> recover) {
		PromiseImpl<Object> p = new PromiseImpl<>(executor);

		synchronized (this) {
			if (next != null) throw new IllegalStateException("子Promise已存在");
			if (recover != null) {
				if (rejectHandler != null) throw new IllegalStateException("异常处理器已存在");
				rejectHandler = recover;
			}
			resolveHandler = fn;
			next = p;
		}

		_apply();
		return p;
	}

	private Promise<T> setRejectHandler(Function<Throwable, ?> recover) {
		synchronized (this) {
			if (rejectHandler != null) throw new IllegalStateException("异常处理器已存在");
			rejectHandler = recover;
		}

		_apply();
		return this;
	}

	@Override public final Promise<T> rejected(Function<Throwable, T> recover) {return setRejectHandler(recover);}
	@Override public final Promise<T> rejectedCompose(Function<Throwable, Promise<T>> recover) {return setRejectHandler(recover);}
	@Override public final Promise<T> rejectedAsync(Executor executor, Function<Throwable, T> recover) {
		this.rejectExecutor = executor;
		return setRejectHandler(recover);
	}
	@Override public final Promise<T> rejectedComposeAsync(Executor executor, Function<Throwable, T> recover) {
		this.rejectExecutor = executor;
		return setRejectHandler(recover);
	}

	private static final Logger LOGGER = Logger.getLogger("Promise");

	private void _apply() {
		int state = this.state;
		if ((state&TASK_COMPLETE) == 0) return;

		synchronized (this) { notifyAll(); }

		if ((state&TASK_SUCCESS) != 0) {
			if (resolveHandler == null) {
				notifyNext(state);
			} else if (once(INVOKE_HANDLER)) {
				if (executor == null) run();
				else executor.execute(this);
			}
		} else if (once(INVOKE_HANDLER)) {
			// 异常无论如何都会执行

			var exception = (Throwable) result;
			PromiseImpl<?> promise = this;

			// 循环往后查找第一个非空的rejectHandler，或者打印
			while (promise != null) {
				if (promise.rejectHandler != null) {
					try {
						Object recover = promise.rejectHandler.apply(exception);

						if (promise == this) this.state = state&CALLBACK;
						if (promise.rejectExecutor != null) promise.executor = promise.rejectExecutor;

						// 用故障恢复值重新resolve
						promise.resolve(recover);

						return;
					} catch (Throwable ex) {
						// rejectHandler发生了异常
						exception = ex;
					}
				}

				int pState = promise.state&CALLBACK;
				if (STATE.compareAndSet(promise, pState, pState|TASK_COMPLETE|INVOKE_HANDLER))
					promise.result = exception;

				promise = promise.next;
			}

			if (notifyNext(state)) return;

			LOGGER.error("异步{}发生了异常", (Throwable) result, this);
		}
	}

	// next在等待当前Promise完成吗
	private boolean notifyNext(int state) {
		if ((state&CALLBACK) != 0 && STATE.compareAndSet(next, next.state&(CALLBACK|WAIT), state)) {
			do {
				state = this.state;
			} while (!STATE.compareAndSet(this, state, state & ~CALLBACK));

			next.result = result;
			next._apply();
			return true;
		}
		return false;
	}

	private boolean once(int flag) {
		while (true) {
			int st = state;
			if ((st&flag) != 0) return false;
			if (STATE.compareAndSet(this, st, st|flag)) return true;
		}
	}

	@Override
	public void run() {
		PromiseImpl<?> p = next;
		try {
			resolveHandler.accept(Helpers.cast(result), p);
		} catch (Throwable e) {
			p.reject(e);
		}
	}

	// region PromiseValue
	@Override
	public void resolve(Object value) {
		if (value instanceof PromiseImpl<?> waitFor) {
			// CALLBACK: 这个Promise需要在完成时通知next，next必须是WAIT状态
			int myState = state&CALLBACK;
			// WAIT: 这个Promise在等待某一个Promise
			if (STATE.compareAndSet(this, myState, myState|WAIT)) {
				while (true) {
					int val = waitFor.state;
					if ((val & CALLBACK) != 0) {
						state = TASK_COMPLETE;
						result = new IllegalStateException(waitFor+" bound to other Promise");
						break;
					} else if ((val&3) == PENDING) {
						if (STATE.compareAndSet(waitFor, val, val|CALLBACK)) {
							synchronized (waitFor) {
								if (waitFor.next != null) {
									state = TASK_COMPLETE;
									result = new IllegalStateException("子Promise已存在");
									break;
								}
								waitFor.next = this;
							}
							break;
						}
					} else {
						state = waitFor.state;
						result = waitFor.result;
						break;
					}
				}
			}
		}

		finish(TASK_COMPLETE|TASK_SUCCESS, value);
	}
	@Override public void resolveOn(Object value, Executor executor1) {
		executor = executor1;
		resolve(value);
	}
	@Override public void reject(Throwable reason) {finish(TASK_COMPLETE, reason);}
	@Override public void rejectOn(Throwable reason, Executor executor1) {
		executor = executor1;
		reject(reason);
	}

	private void finish(int target, Object value) {
		int s = state&CALLBACK;
		if (STATE.compareAndSet(this, s, s|target)) result = value;
		_apply();
	}
	// endregion

	public byte state() { return (byte) (state&3); }

	@Override
	@SuppressWarnings("unchecked")
	public T getNow() {
		if ((state&TASK_SUCCESS) == 0) throw new IllegalStateException();
		return (T) result;
	}
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

	@SuppressWarnings("unchecked")
	private T getOrThrow() throws ExecutionException {
		switch (state &(TASK_COMPLETE|TASK_SUCCESS)) {
			default: throw new IllegalStateException("unknown state "+state);
			case TASK_COMPLETE:
				if (result instanceof CancellationException) throw (CancellationException) result;
				if (result instanceof ExecutionException) throw (ExecutionException) result;
				throw new ExecutionException((Throwable) result);
			case TASK_COMPLETE|TASK_SUCCESS: return (T) result;
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (isDone()) return false;
		reject(new CancellationException());
		return true;
	}

	@Override
	public boolean isCancelled() { return (state&(TASK_COMPLETE|TASK_SUCCESS)) == TASK_COMPLETE && result instanceof CancellationException; }
	public boolean isDone() { return (state&TASK_COMPLETE) != 0; }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Promise@").append(Integer.toHexString(hashCode())).append('{');
		if ((state&TASK_COMPLETE) == 0) {
			if ((state&WAIT) != 0) sb.append("<pending for another promise>");
			else sb.append("<pending>");
		} else {
			sb.append('<').append(state() == FULFILLED ? "fulfilled" : "rejected").append(": ").append(result).append('>');
		}
		if ((state&CALLBACK) != 0) sb.append(", callback=").append(next);
		if (resolveHandler == null) sb.append(", tail");
		return sb.append("}").toString();
	}
}