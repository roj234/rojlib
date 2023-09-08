package roj.concurrent;

import roj.util.Helpers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:54
 */
public interface Promise<T> {
	static <T> Promise<T> sync(Consumer<PromiseCallback> handler) { return new PromiseImpl<>(null, handler); }
	static <T> Promise<T> async(TaskHandler e, Consumer<PromiseCallback> handler) { return new PromiseImpl<>(e, handler); }
	static <T> Promise<T> resolve(T t) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p._state = FULFILLED;
		p._val = t;
		return p;
	}
	static <T> Promise<T> reject(Object t) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p._state = REJECTED;
		p._val = t;
		return p;
	}
	static Promise<Object[]> all(TaskHandler e, Promise<?>... arr) {
		if (arr.length == 0) throw new IllegalArgumentException();

		AtomicInteger remain = new AtomicInteger(arr.length);
		return new PromiseImpl<>(e, op -> {
			Object[] result = new Object[arr.length];
			for (int i = 0; i < arr.length; i++) {
				int no = i;
				arr[i].then((v, o) -> {
					result[no] = v;

					if (remain.decrementAndGet() == 0)
						op.resolve(result);
				});
			}
		});
	}
	@SafeVarargs
	static <T> Promise<T> race(TaskHandler e, Promise<T>... arr) {
		if (arr.length == 0) throw new IllegalArgumentException();
		if (arr.length == 1) return arr[0];

		return new PromiseImpl<>(e, (op) -> {
			BiConsumer<T, PromiseCallback> h = (v, o) -> op.resolve(v);
			for (Promise<T> p : arr) p.then(h);
		});
	}

	default Promise<Void> thenR(Runnable fn) { return Helpers.cast(then((v, o) -> fn.run(), null)); }
	@SuppressWarnings("unchecked")
	default <NEXT> Promise<Object> thenF(Function<T, NEXT> fn) { return then((v, o) -> o.resolve(fn.apply(v)), null); }

	default Promise<Object> then(BiConsumer<T, PromiseCallback> fn) { return then(fn, null); }
	Promise<Object> then(BiConsumer<T, PromiseCallback> fn, Consumer<Promise<?>> fail);
	Promise<T> catch_(Consumer<Promise<?>> fn);
	Promise<T> catch_ES(Function<?, ?> fn);
	Promise<T> finally_(Consumer<Promise<?>> fn);

	int PENDING = 0, FULFILLED = PromiseImpl.TASK_COMPLETE|PromiseImpl.TASK_SUCCESS, REJECTED = PromiseImpl.TASK_COMPLETE;
	byte state();
	T get();

	interface PromiseCallback {
		void resolve(Object result);
		void reject(Object reason);
	}
}
