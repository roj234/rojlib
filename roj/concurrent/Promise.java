package roj.concurrent;

import roj.util.Helpers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:54
 */
public interface Promise<T> extends Future<T> {
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
	static Promise<Object[]> all(TaskHandler e, Promise<?>... arr) { return all(e, Arrays.asList(arr)); }
	static <T extends Promise<?>> Promise<Object[]> all(TaskHandler e, List<T> arr) {
		if (arr.size() == 0) throw new IllegalArgumentException();

		AtomicInteger remain = new AtomicInteger(arr.size());
		Object[] result = new Object[arr.size()];

		PromiseImpl<Object[]> ret = new PromiseImpl<>(e, null);
		for (int i = 0; i < arr.size(); i++) {
			int no = i;
			arr.get(i).then((v, o) -> {
				result[no] = v;

				if (remain.decrementAndGet() == 0)
					ret.resolve(result);
			});
		}
		return ret;
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
	default <NEXT> Promise<Object> thenF(Function<T, NEXT> fn) { return then((v, o) -> o.resolve(fn.apply(v)), null); }

	default Promise<Object> then(BiConsumer<T, PromiseCallback> fn) { return then(fn, null); }
	Promise<Object> then(BiConsumer<T, PromiseCallback> fn, Consumer<Promise<?>> fail);
	Promise<T> catch_(Consumer<Promise<?>> fn);
	Promise<T> catch_ES(Function<?, ?> fn);
	Promise<T> finally_(Consumer<Promise<?>> fn);

	int PENDING = 0, FULFILLED = PromiseImpl.TASK_COMPLETE|PromiseImpl.TASK_SUCCESS, REJECTED = PromiseImpl.TASK_COMPLETE;
	byte state();
	T getNow();
	T get() throws InterruptedException, ExecutionException;
	T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

	interface PromiseCallback {
		void resolve(Object result);
		void reject(Object reason);
		void resolveOn(Object result, TaskHandler handler);
		void rejectOn(Object reason, TaskHandler handler);
	}
}