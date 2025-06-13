package roj.concurrent;

import roj.util.Helpers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2022/10/7 23:54
 */
public sealed interface Promise<T> permits PromiseImpl {
	@SuppressWarnings("unchecked")
	static <T, X extends Promise<T> & Callback> X sync() { return (X) new PromiseImpl<>(); }

	static <T> Promise<T> async(Consumer<Callback> handler) {return async(TaskPool.common(), handler);}
	static <T> Promise<T> async(TaskExecutor executor, Consumer<Callback> handler) {
		PromiseImpl<T> impl = new PromiseImpl<>(executor);
		executor.submit(() -> {
			try {
				handler.accept(impl);
			} catch (Throwable e) {
				impl.reject(e);
			}
		});
		return impl;
	}

	static <T> Promise<T> callAsync(Callable<T> callable) {return callAsync(TaskPool.common(), callable);}
	static <T> Promise<T> callAsync(TaskExecutor executor, Callable<T> callable) {
		PromiseImpl<T> impl = new PromiseImpl<>(executor);
		executor.submit(() -> {
			try {
				impl.resolve(callable.call());
			} catch (Throwable e) {
				impl.reject(e);
			}
		});
		return impl;
	}
	static <T> Promise<T> supplyAsync(Supplier<T> supplier) {return supplyAsync(TaskPool.common(), supplier);}
	static <T> Promise<T> supplyAsync(TaskExecutor executor, Supplier<T> supplier) {return callAsync(executor, supplier::get);}
	static Promise<Void> runAsync(Runnable runnable) {return runAsync(TaskPool.common(), runnable);}
	static Promise<Void> runAsync(TaskExecutor executor, Runnable runnable) {
		return callAsync(executor, () -> {
			runnable.run();
			return null;
		});
	}

	static <T> Promise<T> resolve(T value) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p._state = FULFILLED;
		p._val = value;
		return p;
	}
	static <T> Promise<T> reject(Throwable exception) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p._state = REJECTED;
		p._val = exception;
		return p;
	}

	static Promise<Object[]> all(TaskExecutor executor, Promise<?>... arr) { return all(executor, Arrays.asList(arr)); }
	static Promise<Object[]> all(TaskExecutor executor, List<Promise<?>> arr) {
		if (arr.size() == 0) throw new IllegalArgumentException();

		AtomicInteger remain = new AtomicInteger(arr.size());
		Object[] result = new Object[arr.size()];

		PromiseImpl<Object[]> all = new PromiseImpl<>(executor);

		for (int i = 0; i < arr.size(); i++) {
			int no = i;
			arr.get(i).then((value, callback) -> {
				result[no] = value;

				if (remain.decrementAndGet() == 0)
					all.resolve(result);
			});
		}
		return all;
	}

	static Promise<Object> anyOf(TaskExecutor executor, Promise<?>... arr) {
		if (arr.length == 0) throw new IllegalArgumentException();
		if (arr.length == 1) return Helpers.cast(arr[0]);

		PromiseImpl<Object> any = new PromiseImpl<>(executor);

		BiConsumer<?, Callback> h = (value, _next) -> any.resolve(value);
		for (Promise<?> p : arr) p.then(Helpers.cast(h));

		return any;
	}

	default Promise<Object> then(BiConsumer<T, Callback> fn) {return then(fn, null);}
	Promise<Object> then(BiConsumer<T, Callback> fn, Function<Throwable, ?> recover);
	default Promise<Void> thenAccept(Consumer<T> fn) {
		return Helpers.cast(then((value, callback) -> {
			fn.accept(value);
			callback.resolve(null);
		}));
	}
	default Promise<Void> thenRun(Runnable fn) {
		return Helpers.cast(then((value, callback) -> {
			fn.run();
			callback.resolve(null);
		}));
	}
	default <NEXT> Promise<NEXT> thenApply(Function<T, NEXT> fn) {return Helpers.cast(then((value, callback) -> callback.resolve(fn.apply(value))));}

	Promise<T> rejected(Function<Throwable, T> recover);
	Promise<T> rejectedAsync(TaskExecutor executor, Function<Throwable, T> recover);
	Promise<T> rejectedCompose(Function<Throwable, Promise<T>> recover);
	Promise<T> rejectedComposeAsync(TaskExecutor executor, Function<Throwable, T> recover);

	int PENDING = 0, FULFILLED = PromiseImpl.TASK_COMPLETE|PromiseImpl.TASK_SUCCESS, REJECTED = PromiseImpl.TASK_COMPLETE;
	byte state();
	T getNow();
	T get() throws InterruptedException, ExecutionException;
	T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

	interface Callback {
		// T | Promise<T>
		void resolve(Object result);
		void reject(Throwable reason);
		void resolveOn(Object result, TaskExecutor handler);
		void rejectOn(Throwable reason, TaskExecutor handler);
	}
}