package roj.concurrent;

import roj.collect.IntMap;
import roj.concurrent.task.ITask;
import roj.util.Helpers;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2022/10/8 0008 0:08
 */
final class PromiseImpl<T> implements Promise<T>, ITask, Promise.PromiseValue {
	 PromiseImpl(TaskHandler executor, Consumer<PromiseValue> handler) {
		this.executor = executor;
		if (executor == null) {
			_execute(this, handler);
			return;
		}

		executor.pushTask(() -> _execute(PromiseImpl.this, handler));
	}

	PromiseImpl() {}

	static final int CALLED = 1, INVOKED = 2, INVOKED_FINALLY = 4;
	byte _state, _subState;

	private Consumer<PromiseValue> _handler;
	private Function<?,?> _fail;
	private PromiseFailer _finally;
	Object _result;

	private PromiseImpl<?> listener, next;
	private TaskHandler executor;

	@Override
	public Promise<Object> then(Consumer<PromiseValue> fn, PromiseFailer fail) {
		if (_handler != null) throw new IllegalStateException("Then already set");
		_handler = fn;

		PromiseImpl<Object> p = new PromiseImpl<>();
		p.executor = executor;
		next = p;

		if (_state == FULFILLED) _apply();
		else if (fail != null) p.catch_(fail);

		return p;
	}

	@Override
	public Promise<T> catch_(PromiseFailer fn) {
		if (_fail != null) throw new IllegalStateException("Fail already set");
		_fail = (o) -> {
			try {
				fn.process(o);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
			return IntMap.UNDEFINED;
		};
		if (_state == REJECTED) _apply();

		return this;
	}

	@Override
	public Promise<T> catch_ES(Function<?, ?> fn) {
		if (_fail != null) throw new IllegalStateException("Fail already set");
		_fail = fn;
		if (_state == REJECTED) _apply();
		return this;
	}

	@Override
	public Promise<T> finally_(PromiseFailer fn) {
		if (_finally != null) throw new IllegalStateException("Finally already set");
		_finally = fn;
		if (_state != PENDING) _apply();

		return this;
	}

	private synchronized void _apply() {
		notifyAll();

		boolean shouldInvoke = false;

		check:
		if ((_subState&INVOKED) == 0) {
			if (_state == FULFILLED) {
				if (_handler == null) break check;

				if (executor == null) {
					shouldInvoke = true;
				} else {
					executor.pushTask(this);
				}
			} else {
				Object _result = this._result;
				PromiseImpl<?> p = this;
				while (p != null) {
					fail:
					if (p._fail != null) {
						try {
							Function<Object,Object> fn = Helpers.cast(p._fail);
							Object ret = fn.apply(_result);
							if (ret == IntMap.UNDEFINED) break fail;

							// 如果 fn 抛出一个错误或返回失败的 Promise... see MDN
							p._subState = PENDING;
							p.resolve(ret);
							p._apply();

							_subState |= INVOKED;
							break;
						} catch (Exception e) {
							_result = e;
						}
					}

					p.reject(_result);
					p = p.next;
				}
			}
		}

		if ((_subState&INVOKED_FINALLY) == 0) {
			PromiseImpl<?> p = this;
			if (p._finally != null) {
				try {
					p._finally.process(_result);
				} catch (Exception e) {
					e.printStackTrace();
				}
				_subState |= INVOKED_FINALLY;
			}
		}

		if (shouldInvoke) execute();
	}

	static void _execute(PromiseImpl<?> p, Consumer<PromiseValue> handler) {
		try {
			handler.accept(p);
			if (p._subState == PENDING) {
				p.resolve(null);
			}
		} catch (Throwable e) {
			p.reject(e);
		}

		if (p._state != PENDING) p._apply();
	}

	@Override
	public void execute() {
		synchronized (next) {
			_execute(next, _handler);
		}
	}

	@Override
	public boolean isCancelled() {
		return _subState != 0;
	}

	@Override
	public void resolve(Object t) {
		if (_subState != PENDING) return;
		_subState = CALLED;

		if1:
		if (t instanceof PromiseImpl) {
			PromiseImpl<?> p1 = (PromiseImpl<?>) t;
			synchronized (p1) {
				if (p1._state == PENDING) {
					if (p1.listener != null) throw new IllegalStateException("then already bound");
					p1.listener = this;
					break if1;
				}
			}

			_state = p1._state;
			_result = p1._result;
		} else {
			_state = FULFILLED;
			_result = t;
		}

		invokeListener();
	}

	@Override
	public void reject(Object o) {
		if (_subState != PENDING) return;
		_state = REJECTED;
		_result = o;
		_subState = CALLED;

		invokeListener();
	}

	private void invokeListener() {
		if (listener != null) {
			listener._state = _state;
			listener._subState = CALLED;
			listener._result = _result;
			executor.pushTask(listener);
		}
	}

	public byte state() {
		return _state;
	}

	public Object get() {
		if (_state == PENDING) throw new IllegalStateException();
		return _result;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Promise@").append(Integer.toHexString(hashCode())).append('{');
		if (_handler == null) {
			sb.append("<wait_for_handler>");
		} else {
			if (_state == 0) {
				sb.append("<pending>");
			} else {
				sb.append("<").append(_state == FULFILLED ? "fulfilled" : "rejected").append(">, value: ").append(_result);
			}
		}
		if (next != null) {
			sb.append(", Nx=").append(next);
		}
		return sb.append("}").toString();
	}
}
