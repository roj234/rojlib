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
package roj.collect;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/5 19:31
 */
public class SoftIntMap<T> extends IntMap<T> {
    public class MyEntry<V> extends IntMap.Entry<V> {
        SoftReference<Object> ref;

        @SuppressWarnings("unchecked")
        protected MyEntry(int k, Object v) {
            super(k, null);
            setValue((V) v);
        }

        @Override
        @SuppressWarnings("unchecked")
        public V setValue(V now) {
            if (now == null)
                throw new NullPointerException("Null value is not supported by SoftIntMap.");
            Object old = NOT_USING;
            if (ref != null) {
                old = ref.get();
                if (old != now) {
                    ref.clear();
                    if (now == NOT_USING)
                        ref = null;
                    else
                        ref = new SoftReference<>(now, queue);
                }
            }
            return (V) old;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V getValue() {
            if (ref == null)
                return (V) NOT_USING;
            Object o = ref.get();
            if (o == null) {
                ref = null;
                o = NOT_USING;
            }
            return (V) o;
        }
    }

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    public void removeDummiedEntries() {
        while (queue.poll() != null) {
            size--;
        }
    }

    @Override
    protected Entry<T> createEntry(int id, T value) {
        return new MyEntry<>(id, value);
    }
}
