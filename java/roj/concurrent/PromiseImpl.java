package roj.concurrent;

import org.jetbrains.annotations.NotNull;
import roj.collect.IntMap;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2022/10/8 0:08
 */
final class PromiseImpl<T> implements Promise<T>, Task, Promise.PromiseCallback {
	PromiseImpl() {}
	PromiseImpl(TaskExecutor pool, Consumer<PromiseCallback> cb) {
		executor = pool;
		if (cb != null) {
			if (pool == null) head(cb);
			else pool.submit(() -> head(cb));
		}
	}
	private void head(Consumer<PromiseCallback> handler) {
		try {
			handler.accept(this);
			//if (_state == PENDING) resolve(null);
		} catch (Throwable e) {
			reject(e);
		}
	}


	private static final long state_offset = ReflectionUtils.fieldOffset(PromiseImpl.class, "_state");

	static final int
		TASK_COMPLETE = 1, TASK_SUCCESS = 2,
		WAIT = 4, CALLBACK = 8,
		INVOKE_HANDLER = 16, INVOKE_FINALLY = 32;
	volatile int _state;

	private BiConsumer<?, PromiseCallback> handler_success;
	private Function<?,?> handler_fail;
	private Consumer<Promise<?>> handler_finally;

	Object _val;

	private PromiseImpl<?> next;
	private TaskExecutor executor;

	@Override
	public Promise<Object> then(BiConsumer<T, PromiseCallback> fn, Consumer<Promise<?>> fail) {
		PromiseImpl<Object> p = new PromiseImpl<>();
		p.executor = executor;

		synchronized (this) {
			if (next != null) throw new IllegalStateException("Then already set");
			handler_success = fn;
			next = p;
		}

		if (fail != null) catch_(fail);
		else _apply();

		return p;
	}

	@Override
	public Promise<T> catch_(Consumer<Promise<?>> fn) {
		return catch_ES((o) -> {
			fn.accept(this);
			return IntMap.UNDEFINED;
		});
	}
	@Override
	public Promise<T> catch_ES(Function<?, ?> fn) {
		synchronized (this) {
			if (handler_fail != null) throw new IllegalStateException("Fail already set");
			handler_fail = fn;
		}

		_apply();
		return this;
	}
	@Override
	public Promise<T> finally_(Consumer<Promise<?>> fn) {
		synchronized (this) {
			if (handler_finally != null) throw new IllegalStateException("Finally already set");
			handler_finally = fn;
		}

		if (_state != PENDING && invokeOnce(INVOKE_FINALLY)) fn.accept(this);
		return this;
	}

	private void _apply() {
		if ((_state&TASK_COMPLETE) == 0) return;

		int state = 0;

		if ((_state&TASK_SUCCESS) != 0) {
			if (handler_success == null) {
				if ((_state&CALLBACK) != 0 && U.compareAndSwapInt(next, state_offset, WAIT, _state)) {
					removeFlag(CALLBACK);

					synchronized (this) {
						next._val = _val;
						next._apply();
						//next = null;
					}
				}
			} else if (invokeOnce(INVOKE_HANDLER)) {
				state = 1;
			}
		} else if (invokeOnce(INVOKE_HANDLER)) {
			Object val = _val;
			PromiseImpl<?> p = this;
			state = 2;

			// 如果和你想的不一样... see MDN
			while (p != null) {
				fail:
				if (p.handler_fail != null) {
					try {
						Function<Object,Object> fn = Helpers.cast(p.handler_fail);
						Object ret = fn.apply(val);
						state = 0;
						if (ret == IntMap.UNDEFINED) break fail;

						p.removeFlag(~CALLBACK);
						p.resolve(ret);
						p._apply();
						if (p == this) return;
						break;
					} catch (Throwable e) {
						val = e;
					}
				}

				var prev = SKIP_PRINT.get();
				SKIP_PRINT.set(val);
				try {
					p.reject(val);
				} finally {
					if (prev != null) SKIP_PRINT.set(prev);
					else SKIP_PRINT.remove();
				}
				p = p.next;
			}
		}

		if (handler_finally != null && invokeOnce(INVOKE_FINALLY)) {
			try {
				handler_finally.accept(this);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		synchronized (this) { notifyAll(); }

		if (state == 1) {
			if (executor == null) execute();
			else executor.submit(this);
		} else if (state == 2) {
			Object v = _val;
			if (SKIP_PRINT.get() == v) return;
			LOGGER.error("{}发生了异常", v instanceof Throwable ? (Throwable) v : new Exception("未捕获的Reject"), this);
		}
	}
	private static final ThreadLocal<Object> SKIP_PRINT = new ThreadLocal<>();
	private static final Logger LOGGER = Logger.getLogger("Promise");

	private boolean invokeOnce(int flag) {
		while (true) {
			int st = _state;
			if ((st&flag) != 0) return false;
			if (U.compareAndSwapInt(this, state_offset, st, st|flag)) return true;
		}
	}
	private void removeFlag(int flag) {
		while (true) {
			int st = _state;
			if ((st&flag) == 0) return;
			if (U.compareAndSwapInt(this, state_offset, st, st^flag)) return;
		}
	}

	@Override
	public void execute() {
		PromiseImpl<?> p = next;
		try {
			handler_success.accept(Helpers.cast(_val), p);
			//if (p._state == PENDING) p.resolve(null);
		} catch (Throwable e) {
			p.reject(e);
		}
	}

	// region PromiseValue
	@Override
	public void resolve(Object result) {
		if (result instanceof PromiseImpl) {
			int s = _state&CALLBACK;
			if (U.compareAndSwapInt(this, state_offset, s, s|WAIT)) {
				PromiseImpl<?> c = (PromiseImpl<?>) result;
				while (true) {
					int val = c._state;
					assert (val & WAIT) == 0;
					if (val == PENDING) {
						if (U.compareAndSwapInt(result, state_offset, PENDING, CALLBACK)) {
							synchronized (c) {
								if (c.next != null) {
									_state = TASK_COMPLETE;
									_val = new IllegalStateException("Then already set");
									break;
								}
								c.next = this;
							}
							break;
						}
					} else if ((val & CALLBACK) != 0) {
						_state = TASK_COMPLETE;
						_val = new IllegalStateException("bound to other Promise");
						break;
					} else if ((val & TASK_COMPLETE) != 0) {
						_state = c._state;
						_val = c._val;
						break;
					}
				}
			}

			return;
		}

		promiseFinish(TASK_COMPLETE|TASK_SUCCESS, result);
	}
	@Override
	public void resolveOn(Object result, TaskExecutor handler) {
		executor = handler;
		resolve(result);
	}
	@Override
	public void reject(Object reason) { promiseFinish(TASK_COMPLETE, reason == null ? new RuntimeException() : reason); }
	@Override
	public void rejectOn(Object reason, TaskExecutor handler) {
		executor = handler;
		reject(reason);
	}

	private void promiseFinish(int target, Object o) {
		int s = _state&CALLBACK;
		if (U.compareAndSwapInt(this, state_offset, s, s|target)) _val = o;
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
				else if (_val instanceof Throwable) throw new ExecutionException((Throwable) _val);
				else {
					String s;
					try { s = String.valueOf(_val); } catch (Throwable e) { s = e.getMessage(); }
					throw new IllegalStateException("execution failed:"+s);
				}
			case TASK_COMPLETE|TASK_SUCCESS: return (T) _val;
		}
	}

	@Override
	public boolean cancel() {
		if (isDone()) return false;
		reject(new CancellationException("user cancelled"));
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
		if (handler_success == null) sb.append(", tail");
		return sb.append("}").toString();
	}
}