package roj.concurrent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Holder.java
 */
public interface Holder<T> {
    T get();

    void set(T t);

    static <X> Holder<X> from(Supplier<X> supplier, Consumer<X> consumer) {
        return new LambdaHolder<>(supplier, consumer);
    }

    static <X> Holder<X> from(X x) {
        return new Reference<>(x);
    }

    static <X> Holder<X> from(List<X> list, int i) {
        return new ListHolder<>(list, i);
    }

    class LambdaHolder<T> implements Holder<T> {
        Supplier<T> supplier;
        Consumer<T> consumer;

        public LambdaHolder(Supplier<T> supplier, Consumer<T> consumer) {
            this.supplier = supplier;
            this.consumer = consumer;
        }

        public T get() {
            return supplier.get();
        }

        public void set(T t) {
            this.consumer.accept(t);
        }
    }

    class Reference<T> implements Holder<T> {
        T t;

        public Reference(T t) {
            this.t = t;
        }

        public T get() {
            return t;
        }

        public void set(T t) {
            this.t = t;
        }
    }

    class ListHolder<T> implements Holder<T> {
        final List<T> list;
        final int index;

        public ListHolder(List<T> list, int index) {
            this.list = list;
            this.index = index;
        }

        public T get() {
            return list.get(index);
        }

        public void set(T t) {
            list.set(index, t);
        }
    }

    class MapHolder<T> implements Holder<T> {
        final Map.Entry<?, T> entry;

        public MapHolder(Map.Entry<?, T> entry) {
            this.entry = entry;
        }

        @Override
        public T get() {
            return entry.getValue();
        }

        @Override
        public void set(T t) {
            entry.setValue(t);
        }
    }
}
