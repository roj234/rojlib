package roj.concurrent;

import org.jetbrains.annotations.NotNull;
import roj.compiler.api.Synchronizable;
import roj.reflect.Unsafe;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2022/10/8 0:08
 */
@Synchronizable
final class PromiseImpl<T> implements Promise<T>, Runnable, Cancellable, Promise.Callback {
	PromiseImpl() {}
	PromiseImpl(Executor executor) {this.executor = executor;}

	private static final long STATE = Unsafe.fieldOffset(PromiseImpl.class, "_state");
	static final int
		TASK_COMPLETE = 1, TASK_SUCCESS = 2,
		WAIT = 4, CALLBACK = 8,
		INVOKE_HANDLER = 16;
	volatile int _state;

	private BiConsumer<?, Callback> resolveHandler;
	private Function<Throwable, ?> rejectHandler;

	Object _val;

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
		int state = _state;
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

			var exception = (Throwable)_val;
			PromiseImpl<?> promise = this;

			// 循环往后查找第一个非空的rejectHandler，或者打印
			while (promise != null) {
				if (promise.rejectHandler != null) {
					try {
						Object recover = promise.rejectHandler.apply(exception);

						if (promise == this) _state = state&CALLBACK;
						if (promise.rejectExecutor != null) promise.executor = promise.rejectExecutor;

						// 用故障恢复值重新resolve
						promise.resolve(recover);

						return;
					} catch (Throwable ex) {
						// rejectHandler发生了异常
						exception = ex;
					}
				}

				int pState = promise._state&CALLBACK;
				if (U.compareAndSetInt(promise, STATE, pState, pState|TASK_COMPLETE|INVOKE_HANDLER))
					promise._val = exception;

				promise = promise.next;
			}

			if (notifyNext(state)) return;

			LOGGER.error("异步{}发生了异常", (Throwable) _val, this);
		}
	}

	// next在等待当前Promise完成吗
	private boolean notifyNext(int state) {
		if ((state&CALLBACK) != 0 && U.compareAndSetInt(next, STATE, next._state&(CALLBACK|WAIT), state)) {
			do {
				state = _state;
			} while (!U.compareAndSetInt(this, STATE, state, state & ~CALLBACK));

			next._val = _val;
			next._apply();
			return true;
		}
		return false;
	}

	private boolean once(int flag) {
		while (true) {
			int st = _state;
			if ((st&flag) != 0) return false;
			if (U.compareAndSetInt(this, STATE, st, st|flag)) return true;
		}
	}

	@Override
	public void run() {
		PromiseImpl<?> p = next;
		try {
			resolveHandler.accept(Helpers.cast(_val), p);
		} catch (Throwable e) {
			p.reject(e);
		}
	}

	// region PromiseValue
	@Override
	public void resolve(Object value) {
		if (value instanceof PromiseImpl<?> waitFor) {
			// CALLBACK: 这个Promise需要在完成时通知next，next必须是WAIT状态
			int myState = _state&CALLBACK;
			// WAIT: 这个Promise在等待某一个Promise
			if (U.compareAndSetInt(this, STATE, myState, myState|WAIT)) {
				while (true) {
					int val = waitFor._state;
					if ((val & CALLBACK) != 0) {
						_state = TASK_COMPLETE;
						_val = new IllegalStateException(waitFor+" bound to other Promise");
						break;
					} else if ((val&3) == PENDING) {
						if (U.compareAndSetInt(waitFor, STATE, val, val|CALLBACK)) {
							synchronized (waitFor) {
								if (waitFor.next != null) {
									_state = TASK_COMPLETE;
									_val = new IllegalStateException("子Promise已存在");
									break;
								}
								waitFor.next = this;
							}
							break;
						}
					} else {
						_state = waitFor._state;
						_val = waitFor._val;
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
		int s = _state&CALLBACK;
		if (U.compareAndSetInt(this, STATE, s, s|target)) _val = value;
		_apply();
	}
	// endregion

	public byte state() { return (byte) (_state&3); }

	@Override
	@SuppressWarnings("unchecked")
	public T getNow() {
		if ((_state&TASK_SUCCESS) == 0) throw new IllegalStateException();
		return (T) _val;
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
		switch (_state&(TASK_COMPLETE|TASK_SUCCESS)) {
			default: throw new IllegalStateException("unknown state "+_state);
			case TASK_COMPLETE:
				if (_val instanceof CancellationException) throw (CancellationException) _val;
				if (_val instanceof ExecutionException) throw (ExecutionException) _val;
				throw new ExecutionException((Throwable) _val);
			case TASK_COMPLETE|TASK_SUCCESS: return (T) _val;
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (isDone()) return false;
		reject(new CancellationException());
		return true;
	}

	@Override
	public boolean isCancelled() { return (_state&(TASK_COMPLETE|TASK_SUCCESS)) == TASK_COMPLETE && _val instanceof CancellationException; }
	public boolean isDone() { return (_state&TASK_COMPLETE) != 0; }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Promise@").append(Integer.toHexString(hashCode())).append('{');
		if ((_state&TASK_COMPLETE) == 0) {
			if ((_state&WAIT) != 0) sb.append("<pending for another promise>");
			else sb.append("<pending>");
		} else {
			sb.append('<').append(state() == FULFILLED ? "fulfilled" : "rejected").append(": ").append(_val).append('>');
		}
		if ((_state&CALLBACK) != 0) sb.append(", callback=").append(next);
		if (resolveHandler == null) sb.append(", tail");
		return sb.append("}").toString();
	}
}