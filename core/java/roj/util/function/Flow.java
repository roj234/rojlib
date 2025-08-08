package roj.util.function;

import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.text.TextReader;
import roj.ui.Completion;
import roj.util.Helpers;
import roj.util.OperationDone;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;

/**
 * @author Roj234
 * @since 2024/11/20 0:34
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

	void forEach(Consumer<T> consumer);
	default void safeForEach(Consumer<T> consumer) {
		try {
			forEach(consumer);
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

	default Flow<T> prepend(Flow<T> before) {
		return c -> {
			before.forEach(c);
			this.forEach(c);
		};
	}
	default Flow<T> append(Flow<T> after) {
		return c -> {
			this.forEach(c);
			after.forEach(c);
		};
	}

	default <R> Flow<R> map(Function<T, R> mapper) {return c -> forEach(t -> c.accept(mapper.apply(t)));}
	default <R> Flow<R> map(Function<T, R> mapper1, int count, Function<T, R> mapper2) {
		return c -> {
			var counter = new AtomicInteger(count);
			safeForEach(t -> {
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
	default <R> Flow<R> flatMap(Function<T, Flow<R>> mapper) {return c -> forEach(t -> mapper.apply(t).forEach(c));}
	default Flow<T> filter(Predicate<T> filter) {
		return c -> forEach(t -> {
			if (filter.test(t)) {
				c.accept(t);
			}
		});
	}
	default Flow<T> limit(int max) {
		return c -> {
			var counter = new AtomicInteger(max);
			safeForEach(t -> {
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
			forEach(t -> {
				if (counter.get() <= 0) {
					c.accept(t);
				} else {
					counter.getAndDecrement();
				}
			});
		};
	}
	default Flow<T> andThen(Consumer<T> consumer) {return c -> forEach(consumer.andThen(c));}
	default <U, R> Flow<R> zip(Iterable<U> newParam, BiFunction<T, U, R> combiner) {
		return c -> {
			var itr = newParam.iterator();
			safeForEach(t -> {
				if (itr.hasNext()) {
					c.accept(combiner.apply(t, itr.next()));
				} else {
					throw OperationDone.INSTANCE;
				}
			});
		};
	}

	default Flow<T> sorted() {return sorted(null);}
	default Flow<T> sorted(Comparator<T> comparator) {
		return c -> {
			List<T> list = toList();
			list.sort(comparator);
			for (T t : list) c.accept(t);
		};
	}

	default Flow<T> cached() {
		var arraySeq = new ArrayList<T>();
		forEach(arraySeq::add);
		return arraySeq::forEach;
	}
	default Flow<T> parallel() {
		var pool = ForkJoinPool.commonPool();
		return c -> map(t -> pool.submit(() -> c.accept(t))).cached().forEach(ForkJoinTask::join);
	}

	default Optional<T> findFirst() {
		var ref = new AtomicReference<T>();
		safeForEach(t -> {
			ref.setPlain(t);
			throw OperationDone.INSTANCE;
		});
		return Optional.ofNullable(ref.get());
	}

	default boolean anyMatch(Predicate<T> filter) {
		try {
			forEach(t -> {
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
			forEach(t -> {
				if (!filter.test(t)) throw OperationDone.INSTANCE;
			});
			return true;
		} catch (OperationDone e) {
			return false;
		}
	}

	default String join(String sep) {
		var joiner = new StringJoiner(sep);
		forEach(t -> joiner.add(t.toString()));
		return joiner.toString();
	}

	default List<T> toList() {
		var list = new ArrayList<T>();
		forEach(list::add);
		return list;
	}
	default Set<T> toSet() {
		var set = new HashSet<T>();
		forEach(set::add);
		return set;
	}
	default Object[] toArray() {return toList().toArray();}
	default T[] toArray(T[] o) {return toList().toArray(o);}
	default T[] toArray(IntFunction<T[]> o) {
		List<T> list = toList();
		return list.toArray(o.apply(list.size()));
	}

	default <U> U reduce(U identity, BiFunction<U, T, U> accumulator, BiFunction<U, U, U> combiner) {
		AtomicInteger v = new AtomicInteger();
		ThreadLocal<Integer> threadId = ThreadLocal.withInitial(v::getAndIncrement);
		List<U> tmp = new ArrayList<>();
		Object lock = new Object();
		forEach(t -> {
			var i = threadId.get();
			U value;
			if (tmp.size() <= i) {
				synchronized (lock) {
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
	default <K, V> Map<K, V> groupBy(Function<T, K> grouper, Supplier<V> identity, BiFunction<V, T, V> reducer) {
		var map = new HashMap<K, V>();

		forEach(t -> {
			var i = grouper.apply(t);
			V v1 = map.get(i);
			if (v1 == null && !map.containsKey(i))
				v1 = identity.get();
			map.put(i, reducer.apply(v1, t));
		});

		return map;
	}

	default long count() {
		var adder = new LongAdder();
		forEach(t -> adder.increment());
		return adder.sum();
	}
}
