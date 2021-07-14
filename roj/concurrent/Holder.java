/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.concurrent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
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
