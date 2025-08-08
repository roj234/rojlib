package roj.concurrent;

import roj.util.Helpers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2022/10/7 23:54
 */
public sealed interface Promise<T> extends Future<T> permits PromiseImpl {
	@SuppressWarnings("unchecked")
	static <T, X extends Promise<T> & Callback> X sync() { return (X) new PromiseImpl<>(); }

	static <T> Promise<T> async(Consumer<Callback> handler) {return async(TaskPool.common(), handler);}
	static <T> Promise<T> async(Executor executor, Consumer<Callback> handler) {
		PromiseImpl<T> impl = new PromiseImpl<>(executor);
		executor.execute(() -> {
			try {
				handler.accept(impl);
			} catch (Throwable e) {
				impl.reject(e);
			}
		});
		return impl;
	}

	static <T> Promise<T> callAsync(Callable<T> callable) {return callAsync(TaskPool.common(), callable);}
	static <T> Promise<T> callAsync(Executor executor, Callable<T> callable) {
		PromiseImpl<T> impl = new PromiseImpl<>(executor);
		executor.execute(() -> {
			try {
				impl.resolve(callable.call());
			} catch (Throwable e) {
				impl.reject(e);
			}
		});
		return impl;
	}
	static <T> Promise<T> supplyAsync(Supplier<T> supplier) {return supplyAsync(TaskPool.common(), supplier);}
	static <T> Promise<T> supplyAsync(Executor executor, Supplier<T> supplier) {return callAsync(executor, supplier::get);}
	static Promise<Void> runAsync(Runnable runnable) {return runAsync(TaskPool.common(), runnable);}
	static Promise<Void> runAsync(Executor executor, Runnable runnable) {
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

	static Promise<Object[]> all(Executor executor, Promise<?>... children) { return all(executor, Arrays.asList(children)); }
	static Promise<Object[]> all(Executor executor, List<Promise<?>> children) {
		if (children.size() == 0) throw new IllegalArgumentException();

		AtomicInteger remain = new AtomicInteger(children.size());
		Object[] result = new Object[children.size()];

		PromiseImpl<Object[]> all = new PromiseImpl<>(executor);

		for (int i = 0; i < children.size(); i++) {
			int no = i;
			children.get(i).then((value, callback) -> {
				result[no] = value;

				if (remain.decrementAndGet() == 0)
					all.resolve(result);
			});
		}
		return all;
	}

	@SafeVarargs
	static <T> Promise<T> any(Executor executor, Promise<? extends T>... children) {return any(executor, Arrays.asList(children));}
	static <T> Promise<T> any(Executor executor, List<Promise<? extends T>> children) {
		if (children.size() == 0) throw new IllegalArgumentException();
		if (children.size() == 1) return Helpers.cast(children.get(0));

		PromiseImpl<T> any = new PromiseImpl<>(executor);

		BiConsumer<?, Callback> h = (value, _next) -> any.resolve(value);
		for (Promise<?> p : children) p.then(Helpers.cast(h));

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
	Promise<T> rejectedAsync(Executor executor, Function<Throwable, T> recover);
	Promise<T> rejectedCompose(Function<Throwable, Promise<T>> recover);
	Promise<T> rejectedComposeAsync(Executor executor, Function<Throwable, T> recover);

	int PENDING = 0, FULFILLED = PromiseImpl.TASK_COMPLETE|PromiseImpl.TASK_SUCCESS, REJECTED = PromiseImpl.TASK_COMPLETE;
	byte state();
	T getNow();
	T get() throws InterruptedException, ExecutionException;
	T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

	interface Callback {
		// T | Promise<T>
		void resolve(Object result);
		void reject(Throwable reason);
		void resolveOn(Object result, Executor handler);
		void rejectOn(Throwable reason, Executor handler);
	}
}