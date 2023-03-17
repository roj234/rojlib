package roj.concurrent;

import roj.util.Helpers;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:54
 */
public interface Promise<T> {
	static <T> Promise<T> new_(TaskHandler e, Consumer<PromiseValue> handler) {
		return new PromiseImpl<>(e, handler);
	}
	static <T> Promise<T> resolve(T t) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p._state = FULFILLED;
		p._result = t;
		return p;
	}
	static <T> Promise<T> reject(Object t) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p._state = REJECTED;
		p._result = t;
		return p;
	}
	static Promise<Object[]> all(TaskHandler e, Promise<?>... arr) {
		if (arr.length == 0) throw new IllegalArgumentException();

		return new PromiseImpl<>(e, new Consumer<PromiseValue>() {
			int remain = arr.length;

			@Override
			public void accept(PromiseValue op) {
				Object[] result = new Object[arr.length];
				for (int i = 0; i < arr.length; i++) {

					int finalI = i;
					arr[i].thenV((op1) -> {
						result[finalI] = op1.get();
						if (--remain == 0) {
							op.resolve(result);
						}
					});
				}
			}
		});
	}
	@SafeVarargs
	static <T> Promise<T> race(TaskHandler e, Promise<T>... arr) {
		if (arr.length == 0) throw new IllegalArgumentException();
		if (arr.length == 1) return arr[0];

		return new PromiseImpl<>(e, (op) -> {
			Consumer<PromiseValue> h = (op1) -> op.resolve(op1.get());
			for (Promise<T> p : arr) p.thenV(h);
		});
	}

	default Promise<Void> then(Runnable fn) {
		return Helpers.cast(then((opr) -> fn.run(), null));
	}
	@SuppressWarnings("unchecked")
	default <NEXT> Promise<NEXT> then(Function<T, NEXT> fn) {
		return (Promise<NEXT>) then((opr) -> opr.resolve(fn.apply((T) opr.get())), null);
	}
	default Promise<Object> thenV(Consumer<PromiseValue> fn) {
		return then(fn, null);
	}
	Promise<Object> then(Consumer<PromiseValue> fn, PromiseFailer fail);
	Promise<T> catch_(PromiseFailer fn);
	Promise<T> catch_ES(Function<?, ?> fn);
	Promise<T> finally_(PromiseFailer fn);

	int PENDING = 0, FULFILLED = 1, REJECTED = -1;
	byte state();
	Object get();

	interface PromiseFailer {
		void process(Object value) throws Exception;
	}

	interface PromiseValue {
		Object get();
		void resolve(Object t);
		void reject(Object o);
	}
}
