/*
 * This file is a part of MoreItems
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
package roj.util;

import roj.collect.LongBitSet;
import roj.reflect.DirectAccessor;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2021/9/13 12:48
 */
public class FastThreadLocal<T> {
    private static final ThreadLocal<Object[]> slowGetter = new ThreadLocal<>();

    private static int registrations;
    private static final LongBitSet reusable = new LongBitSet();

    private static Thread[] threads;
    private static BiFunction<Object, Object, Object> getMap;
    private static BiConsumer<Object, Object> remove;

    @SuppressWarnings("unchecked")
    private static void init() {
        try {
            getMap = DirectAccessor
                    .builder(BiFunction.class).delegate_o(ThreadLocal.class, "getMap", "apply")
                    .build();
            remove = DirectAccessor
                    .builder(BiConsumer.class)
                    .delegate_o(getMap.apply(slowGetter, Thread.currentThread()).getClass(), "remove", "accept")
                    .build();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public final int seqNum;

    public FastThreadLocal() {
        synchronized (slowGetter) {
            int firstUsable = reusable.first();
            if (firstUsable >= 0) {
                reusable.remove(firstUsable);
                this.seqNum = firstUsable;
            } else {
                this.seqNum = registrations++;
            }
        }
    }

    public static <T> FastThreadLocal<T> withInitial(Supplier<T> s) {
        return new FastThreadLocal<T>() {
            @Override
            protected T initialValue() {
                return s.get();
            }
        };
    }

    @Override
    protected void finalize() {
        synchronized (slowGetter) {
            reusable.add(seqNum);
        }
        clearAll();
    }

    public void clearAll() {
        ThreadGroup g = Thread.currentThread().getThreadGroup();
        while (g.getParent() != null) g = g.getParent();

        Thread[] t = threads;
        int c = g.activeCount();
        if (t == null || t.length < c) {
            synchronized (reusable) { // 换个锁
                t = threads;
                if (t == null || t.length < c) {
                    t = threads = new Thread[c];
                }
            }
        }

        if (getMap == null) init();

        c = g.enumerate(t);
        while (c-- > 0) {
            if (t[c] instanceof FastLocalThread) {
                FastLocalThread flt = (FastLocalThread) t[c];
                if (flt.localDataArray.length >= seqNum) {
                    synchronized (flt.arrayLock) {
                        // 要么没复制，要么复制完了而且新数组已设置
                        flt.localDataArray[seqNum] = null;
                    }
                }
            } else {
                if (getMap == null) continue;
                remove.accept(getMap.apply(slowGetter, t[c]), slowGetter);
            }
        }
    }

    protected T initialValue() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        Object[] x = getDataHolder(seqNum);
        if(x[seqNum] == null) x[seqNum] = initialValue();
        return (T) x[seqNum];
    }

    public void set(T v) {
        getDataHolder(seqNum)[seqNum] = v;
    }

    public static Object[] getDataHolder(int seqNum) {
        Thread t = Thread.currentThread();
        if(t instanceof FastLocalThread) {
            FastLocalThread t1 = (FastLocalThread) t;
            Object[] data = t1.localDataArray;
            if(data.length <= seqNum) {
                Object[] oldArray = data;
                data = new Object[seqNum + 1];
                synchronized (t1.arrayLock) {
                    if (oldArray.length > 0) {
                        System.arraycopy(oldArray, 0, data, 0, oldArray.length);
                    }
                    t1.localDataArray = data;
                }
            }
            return data;
        }

        Object[] x = slowGetter.get();
        if(x == null || x.length <= seqNum) {
            Object[] oldArray = x;
            x = new Object[seqNum + 1];
            if(oldArray != null) {
                System.arraycopy(oldArray, 0, x, 0, oldArray.length);
            }
            slowGetter.set(x);
        }
        return x;
    }

    @Deprecated
    public void remove() {
        set(null);
    }
}
