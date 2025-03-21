package roj.concurrent;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2024/11/20 0020 0:34
 */
@FunctionalInterface
public interface Flow<T> {
	static <A, B> Function<A, B> terminator() {
		return x -> {throw OperationDone.INSTANCE;};
	}
	static <A, B> Function<A, B> terminator(B lastOut) {
		boolean[] a = new boolean[1];
		return x -> {
			if (a[0]) throw OperationDone.INSTANCE;
			a[0] = true;
			return lastOut;
		};
	}

	void consume(Consumer<T> consumer);
	default void safeConsume(Consumer<T> consumer) {
		try {
			consume(consumer);
		} catch (OperationDone ignored) {}
	}

	/**
	 * Create empty flow
	 */
	static <T> Flow<T> of() {return c -> {};}
	static <T> Flow<T> of(T t) {return c -> c.accept(t);}
	@SafeVarargs static <T> Flow<T> of(T... t) {return of(Arrays.asList(t));}
	static <T> Flow<T> of(Iterable<T> t) {return t::forEach;}
	static Flow<String> linesOf(File file) throws IOException {
		return c -> {
			try (var tr = TextReader.auto(file)) {
				for (String line : tr) {
					c.accept(line);
				}
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		};
	}

	default <R> Flow<R> map(Function<T, R> mapper) {return c -> consume(t -> c.accept(mapper.apply(t)));}
	default <R> Flow<R> map(Function<T, R> mapper1, int count, Function<T, R> mapper2) {
		return c -> {
			var counter = new AtomicInteger(count);
			safeConsume(t -> {
				Function<T, R> mapper;
				if (counter.get() > 0) {
					counter.decrementAndGet();
					mapper = mapper1;
				} else {
					mapper = mapper2;
				}
				R result = mapper.apply(t);
				if (result != IntMap.UNDEFINED)
					c.accept(result);
			});
		};
	}
	default <R> Flow<R> flatMap(Function<T, Flow<R>> mapper) {return c -> consume(t -> mapper.apply(t).consume(c));}
	default Flow<T> filter(Predicate<T> filter) {
		return c -> consume(t -> {
			if (filter.test(t)) {
				c.accept(t);
			}
		});
	}
	default Flow<T> limit(int max) {
		return c -> {
			var counter = new AtomicInteger(max);
			safeConsume(t -> {
				if (counter.getAndDecrement() > 0) {
					c.accept(t);
				} else {
					throw OperationDone.INSTANCE;
				}
			});
		};
	}
	default Flow<T> skip(int count) {
		return c -> {
			var counter = new AtomicInteger(count);
			consume(t -> {
				if (counter.get() <= 0) {
					c.accept(t);
				} else {
					counter.getAndDecrement();
				}
			});
		};
	}
	default Flow<T> andThen(Consumer<T> consumer) {return c -> consume(consumer.andThen(c));}
	default <U, R> Flow<R> zip(Iterable<U> newParam, BiFunction<T, U, R> combiner) {
		return c -> {
			var itr = newParam.iterator();
			safeConsume(t -> {
				if (itr.hasNext()) {
					c.accept(combiner.apply(t, itr.next()));
				} else {
					throw OperationDone.INSTANCE;
				}
			});
		};
	}

	default Flow<T> cached() {
		var arraySeq = new ArrayList<T>();
		consume(arraySeq::add);
		return arraySeq::forEach;
	}
	default Flow<T> parallel() {
		var pool = ForkJoinPool.commonPool();
		return c -> map(t -> pool.submit(() -> c.accept(t))).cached().consume(ForkJoinTask::join);
	}

	default boolean anyMatch(Predicate<T> filter) {
		try {
			consume(t -> {
				if (filter.test(t)) throw OperationDone.INSTANCE;
			});
			return false;
		} catch (OperationDone e) {
			return true;
		}
	}
	default boolean noneMatch(Predicate<T> filter) {return !anyMatch(filter);}
	default boolean allMatch(Predicate<T> filter) {
		try {
			consume(t -> {
				if (!filter.test(t)) throw OperationDone.INSTANCE;
			});
			return true;
		} catch (OperationDone e) {
			return false;
		}
	}

	default String join(String sep) {
		var joiner = new StringJoiner(sep);
		consume(t -> joiner.add(t.toString()));
		return joiner.toString();
	}

	default List<T> toList() {
		var list = new SimpleList<T>();
		consume(list::add);
		return list;
	}
	default Set<T> toSet() {
		var set = new MyHashSet<T>();
		consume(set::add);
		return set;
	}

	default <U> U reduce(U identity, BiFunction<U, T, U> accumulator, BiFunction<U, U, U> combiner) {
		AtomicInteger v = new AtomicInteger();
		ThreadLocal<Integer> threadId = ThreadLocal.withInitial(v::getAndIncrement);
		List<U> tmp = new SimpleList<>();
		consume(t -> {
			var i = threadId.get();
			U value;
			if (tmp.size() <= i) {
				synchronized (tmp) {
					while (tmp.size() <= i)
						tmp.add(null);
				}
				value = identity;
			} else {
				value = tmp.get(i);
			}

			tmp.set(i, accumulator.apply(value, t));
		});

		U value = identity;
		for (U u : tmp) {
			value = combiner.apply(value, u);
		}

		return value;
	}
	default <K, V> Map<K, V> groupBy(Function<T, K> grouper, V identity, BiFunction<V, T, V> reducer) {
		var map = new MyHashMap<K, V>();

		consume(t -> {
			var i = grouper.apply(t);
			V v1 = map.getOrDefault(i, identity);
			map.put(i, reducer.apply(v1, t));
		});

		return map;
	}

	default long count() {
		var adder = new LongAdder();
		consume(t -> adder.increment());
		return adder.sum();
	}
}
