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

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/13 12:48
 */
public class FastThreadLocal<T> {
    private static int registrations;
    private static final ThreadLocal<Object[]> slowGetter = new ThreadLocal<>();

    public final int seqNum;

    public FastThreadLocal() {
        synchronized (FastThreadLocal.class) {
            this.seqNum = registrations++;
        }
    }

    protected T initialValue() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        Object[] x = getDataHolder();
        if(x[seqNum] == null)
            x[seqNum] = initialValue();
        return (T) x[seqNum];
    }

    public void set(T v) {
        getDataHolder()[seqNum] = v;
    }

    public Object[] getDataHolder() {
        Thread t = Thread.currentThread();
        if(t instanceof FastLocalThread) {
            FastLocalThread t1 = (FastLocalThread) t;
            Object[] data = t1.localDataArray;
            if(data.length <= seqNum) {
                Object[] oldArray = data;
                data = new Object[seqNum + 1];
                if(oldArray.length > 0) {
                    System.arraycopy(oldArray, 0, data, 0, oldArray.length);
                }
                t1.localDataArray = data;
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
