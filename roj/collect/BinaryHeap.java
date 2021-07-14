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


import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/2 19:59
 */
public class BinaryHeap<T> implements Collection<T> {
    static final int DEF_SIZE = 16;
    static final float INC_RATE = 1.5f;				/* heap自动增长速度 */

    public static final boolean BIG/* 大根二叉堆 */ = true, SMALL/* 小根二叉堆 */ = false;

    Comparator<T> cmp;

    T[] entries;
    int	size;
    int capacity;
    boolean type;

    public BinaryHeap(boolean type, Comparator<T> cmp) {
        this(DEF_SIZE, type, cmp);
    }

    public BinaryHeap(int capacity, boolean type, Comparator<T> cmp) {
        if(capacity <= 1)
            capacity = DEF_SIZE;
        this.capacity = capacity;
        this.type = type;
        this.cmp = cmp;
    }

    /* 增长二叉堆容量 */
    @SuppressWarnings("unchecked")
    public void resize() {
        T[] entriesO = this.entries;

        capacity *= INC_RATE;
        Object[] entriesN = new Object[capacity + 1];

        System.arraycopy(entriesO, 0, entriesN, 0, entriesO.length);
        for (int i = entriesO.length; i < entriesN.length; i++) {
            entriesN[i] = IntMap.NOT_USING;
        }
        this.entries = (T[]) entriesN;
    }

    /* 交换节点内容 */
    static void swap(Object[] n1, int index1, int index2) {
        Object temp;

        if (index1 == index2)
            return;

        temp = n1[index1];
        n1[index1] = n1[index2];
        n1[index2] = temp;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        return remove(indexOf((T) o)) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if(!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T o : c) {
            add(o);
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean fl = false;
        for (Object o : c) {
            fl |= remove(o);
        }
        return fl;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        throw new UnsupportedOperationException("懒得搞");
    }

    /* 入堆操作 */
    public boolean add(T node) {
        if(entries == null)
            init();

        if (size == capacity) {
            resize();
        }

        int child, parent;

        T[] e = this.entries;
        e[child = ++size] = node;

        Comparator<T> cmp = this.cmp;

        /* 减少循环中的判断 */
        if (SMALL == type) {
            /* 小根堆 */
            while(true) {
                if (child < 1)
                    break;
                parent = child;
                child >>= 1;
                if (e[child] == IntMap.NOT_USING || cmp.compare(e[child], e[parent]) > 0) {
                    swap(e, child, parent);
                } else {
                    break;
                }
            }
        } else {
            /* 大根堆 */
            while(true) {
                if (child < 1)
                    break;
                parent = child;
                child >>= 1;
                if (e[child] == IntMap.NOT_USING || cmp.compare(e[child], e[parent]) < 0) {
                    swap(e, child, parent);
                } else {
                    break;
                }
            }
        }

        return true;
    }

    public T remove(int idx) {
        int n, c;
        if (idx < 1 || idx > size)
            throw new ArrayIndexOutOfBoundsException(idx);

        T[] e = this.entries;
        swap(e, n = idx, size--);

        Comparator<T> cmp = this.cmp;
        final int size = this.size;

        /* 减少循环中的判断 */
        if (SMALL == type) {
            /* 小根堆 */
            while (true) {
                c = n;
                n <<= 1;
                if (n > size)
                    break;

                if ((n + 1) > size) {
                    if (cmp.compare(e[c], e[n]) > 0)
                        swap(e, c, n);
				    else
                        break;
                } else {
                    if (cmp.compare(e[n], e[n + 1]) > 0) {
                        if (cmp.compare(e[c], e[n + 1]) > 0) {
                            swap(e, c, ++n);
                        } else
                            break;
                    } else {
                        if (cmp.compare(e[c], e[n]) > 0)
                            swap(e, c, n);
					    else
                            break;
                    }
                }
            }
        } else {
            /* 大根堆 */
            while (true) {
                c = n;
                n <<= 1;
                if (n > size)
                    break;

                if ((n + 1) > this.size) {
                    if (cmp.compare(e[c], e[n]) < 0)
                        swap(e, c, n);
                    else
                        break;
                } else {
                    if (cmp.compare(e[n], e[n + 1]) < 0) {
                        if (0 > cmp.compare(e[c], e[n + 1])) {
                            swap(e, c, ++n);
                        } else
                            break;
                    } else {
                        if (0 > cmp.compare(e[c], e[n]))
                            swap(e, c, n);
                        else
                            break;
                    }
                }
            }
        }

        return (e[size + 1]);
    }

    public T pop() {
        return remove(1);
    }

    public int indexOf(T node) {
        if(entries == null)
            return -1;

        final Comparator<T> cmp = this.cmp;
        final T[] e = this.entries;
        for (int i = 1 ; i <= size; i++) {
            if (cmp.compare(node, e[i]) == 0)
                return i;
        }

        return -1;
    }

    public int indexOfEquals(T node) {
        if(entries == null || node == null)
            return -1;

        final T[] e = this.entries;
        for (int i = 1 ; i <= size; i++) {
            if (node.equals(e[i]))
                return i;
        }

        return -1;
    }

    public T top() {
        return entries[1];
    }

    public T bottom() {
        return entries[size];
    }

    public T get(int idx) {
        return entries[idx + 1];
    }

    @SafeVarargs
    public static <T> BinaryHeap<T> from(Comparator<T> cmp, boolean type, T... list) {
        BinaryHeap<T> binaryHeap = new BinaryHeap<>(list.length + 1, type, cmp);
        binaryHeap.init();
        System.arraycopy(list, 0, binaryHeap.entries, 0, binaryHeap.size = list.length / 2);

        for (int i = list.length / 2; i >= 1; i--) {
            binaryHeap.add(list[i]);
        }

        return binaryHeap;
    }

    @SuppressWarnings("unchecked")
    private void init() {
        entries = (T[]) new Object[capacity + 1];
        //Arrays.fill(entries, IntMap.NOT_USING);
    }

    public void clear() {
        Arrays.fill(entries, IntMap.NOT_USING);
        size = 0;
    }

    @Override
    public String toString() {
        return "BinaryHeap[" + ArrayUtil.toString(entries, 1, size + 1) + ']';
    }

    public void slowClear() {
        if(entries != null) {
            Arrays.fill(entries, null);
            entries = null;
        }
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return indexOf((T) o) != -1;
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return entries == null || size == 0 ? Collections.emptyIterator() : new ArrayIterator<>(entries, 1, size + 1);
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        if(entries == null || size == 0)
            return new Object[0];
        Object[] objs = new Object[size];
        System.arraycopy(entries, 1, objs, 0, size);
        return objs;
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        if(entries == null)
            return a;
        if(a.length < size)
            a = Arrays.copyOf(a, size);
        System.arraycopy(entries, 1, a, 0, size);
        return a;
    }
}
