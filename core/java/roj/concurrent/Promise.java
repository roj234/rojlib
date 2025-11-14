package roj.concurrent;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import roj.util.Helpers;
import roj.util.function.ExceptionalConsumer;
import roj.util.function.ExceptionalFunction;
import roj.util.function.ExceptionalRunnable;

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
	static <T, X extends Promise<T> & Result> X manual() {return manual(TaskPool.common());}
	/**
	 *
	 * @param executor resolve / reject execute on, use null to on same thread (not recommended)
	 */
	@SuppressWarnings("unchecked")
	static <T, X extends Promise<T> & Result> X manual(@Nullable Executor executor) {return (X) new PromiseImpl<>().on(executor);}

	// invokeasync?
	static <T> Promise<T> async(ExceptionalConsumer<Result, Throwable> handler) {return async(TaskPool.common(), handler);}
	static <T> Promise<T> async(Executor executor, ExceptionalConsumer<Result, Throwable> handler) {
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
		p.state = FULFILLED;
		p.result = value;
		return p;
	}
	static <T> Promise<T> reject(Throwable exception) {
		PromiseImpl<T> p = new PromiseImpl<>();
		p.state = REJECTED;
		p.result = exception;
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

		BiConsumer<?, Result> h = (value, _next) -> any.resolve(value);
		for (Promise<?> p : children) p.then(Helpers.cast(h));

		return any;
	}

	@ApiStatus.Internal
	default Promise<Object> then(BiConsumer<T, Result> fn) {return then(fn, null);}
	Promise<Object> then(BiConsumer<T, Result> fn, Function<Throwable, ?> recover);
	@SuppressWarnings("unchecked")
	default Promise<T> thenAccept(ExceptionalConsumer<T, Throwable> fn) {
		return Helpers.cast(then((value, callback) -> {
			((Consumer<T>)fn).accept(value);
			callback.resolve(value);
		}));
	}
	default Promise<T> thenRun(ExceptionalRunnable<Throwable> fn) {
		return Helpers.cast(then((value, callback) -> {
			((Runnable)fn).run();
			callback.resolve(value);
		}));
	}
	@SuppressWarnings("unchecked")
	default <NEXT> Promise<NEXT> thenApply(ExceptionalFunction<T, NEXT, Throwable> fn) {
		return Helpers.cast(then((value, callback) -> callback.resolve(((Function<T, NEXT>)fn).apply(value))));
	}

	Promise<T> exceptionally(Function<Throwable, T> recover);
	Promise<T> exceptionallyCompose(Function<Throwable, Promise<T>> recover);
	Promise<T> exceptionallyAsync(Executor executor, Function<Throwable, T> recover);
	Promise<T> exceptionallyComposeAsync(Executor executor, Function<Throwable, T> recover);

	int PENDING = 0, FULFILLED = PromiseImpl.TASK_COMPLETE|PromiseImpl.TASK_SUCCESS, REJECTED = PromiseImpl.TASK_COMPLETE;
	byte rawState();
	T getNow();
	T get() throws InterruptedException, ExecutionException;
	T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

	interface Result {
		// T | Promise<T>
		void resolve(Object result);
		void reject(Throwable reason);
	}
}