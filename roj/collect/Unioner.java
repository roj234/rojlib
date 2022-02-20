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

import roj.collect.Unioner.Range;
import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Union Finder
 *
 * @author Roj234
 * @since  2021/4/27 18:20
 */
public class Unioner<T extends Range> implements Iterable<Unioner.Region> {
    private static final Region[] EMPTY_DATA = new Region[0];

    public Unioner() {
        this(0, true, 15);
    }

    public Unioner(int capacity) {
        this(capacity, true, 15);
    }

    public Unioner(int capacity, boolean careContent, int points) {
        data = capacity == 0 ? EMPTY_DATA : new Region[capacity << 1];
        care = careContent;
        pointBinSize = points;
    }

    private Region[] data;
    private int size, arrSize;
    private boolean care;

    private int binarySearch(long key) {
        int low = 0;
        int high = arrSize - 1;

        Region[] a = data;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = a[mid].node.pos();

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else
                return mid; // key found
        }

        // low ...

        return -(low + 1);  // key not found.
    }

    private final int pointBinSize;
    private Point pointBin;
    private int   pointCnt;

    protected final Point retain(T owner, boolean end) {
        Point p = pointBin;

        if (p != null) {
            p.owner = owner;
            p.end = end;

            pointBin = p.next;
            p.next = null;
            pointCnt--;
        } else {
            p = new Point(owner, end);
        }
        return p;
    }

    protected final void release(Point p) {
        if (pointBin != null && pointCnt > pointBinSize) {
            return;
        }
        p.owner = null;
        p.next = pointBin;
        pointCnt++;
        pointBin = p;
    }

    public void setCare(boolean care) {
        if (arrSize != 0) throw new IllegalStateException("Not empty");
        this.care = care;
    }

    public boolean getCare() {
        return care;
    }

    public void clear() {
        int i = 0;
        for (; i < arrSize; i++) {
            Region r = data[i];

            r.value.clear();
            Point p = r.node;
            r.node = null;

            while (p != null) {
                Point next = p.next;
                release(p);
                p = next;
            }

            if (pointCnt == pointBinSize) break;
        }
        for (; i < arrSize; i++) {
            Region r = data[i];
            r.node = null;
            r.value.clear();
        }
        arrSize = size = 0;
    }

    public boolean add(T t) {
        long sp = t.startPos();
        if(sp >= t.endPos())
            throw new IndexOutOfBoundsException("start >= end: " + t);

        int begin = binarySearch(sp);
        begin = addPoint(begin, t, false);
        if (begin == -1) return false;

        int end = binarySearch(t.endPos());
        end = addPoint(end, t, true);

        if (care) {
            Region[] d = data;
            for (; begin < end; begin++) {
                d[begin].value.add(t);
            }
        }

        size++;

        return true;
    }

    private int addPoint(int pos, T val, boolean end) {
        Point start = retain(val, end);
        if(pos >= 0) {
            Point point = data[pos].node;
            while (true) {
                if(val.equals(point.owner))
                    return -1;
                if(point.next == null) {
                    point.next = start;
                    break;
                }
                point = point.next;
            }
        } else {
            // 上次clear剩下的
            Region h = arrSize == data.length ? null : data[arrSize];
            if (h == null) h = new Region(care);
            else h.init(care);
            h.node = start;

            pos = -pos - 1;
            if (care && pos > 0) {
                h.value.addAll(data[pos - 1].value);
            }
            insertAt(pos, h);
        }
        return pos;
    }

    public boolean remove(T t) {
        long startPos = t.startPos();
        if(startPos >= t.endPos())
            throw new IndexOutOfBoundsException("start >= end");

        int begin = binarySearch(startPos);
        if (begin < 0 || !removePoint(begin, t)) return false;

        int endPos = binarySearch(t.endPos());
        if (endPos < 0 || !removePoint(endPos, t))
            throw new IllegalStateException("我猜到了开始,却没猜到结局... " + t.startPos() + "," + t.endPos() + ": " + t);

        if(data[endPos] == null)
            endPos--;

        if (care) {
            for (; begin < endPos; begin++) {
                data[begin].value.remove(t);
            }
        }

        size--;

        return true;
    }

    private boolean removePoint(int pos, T val) {
        Point prev = null;
        Point point = data[pos].node;
        while (point != null) {
            if(val.equals(point.owner)) {
                if(prev == null) {
                    if(point.next == null) removeId(pos);
                    else this.data[pos].node = point.next;
                } else {
                    prev.next = point.next;
                }
                release(point);
                return true;
            }
            prev = point;
            point = point.next;
        }
        return false;
//        int[] arr;
//
//        // 从arr中删除一位
//        int j = 1 + (i >>> 5);
//
//        int data = arr[i>>>5], mask = (1 << 31-(i&31)) - 1;
//        arr[i>>>5] = (data & mask) | ((data & ~mask) << 1) | (arr[j] >>> 31);
//
//        while (j < arr.length - 1) {
//            arr[j] = (arr[j++] << 1) | (arr[j] >>> 31);
//        }
//        arr[j] <<= 1;
    }

    public int size() {
        return size;
    }

    public int arraySize() {
        return arrSize;
    }

    public Region findRegion(long key) {
        int pos = binarySearch(key);
        if(pos < 0) {
            // array id now
            if((pos = -pos - 2) < 0) return null;
        }
        return data[pos];
    }

    public int search(long key) {
        int pos = binarySearch(key);
        return pos < 0 ? -pos - 2 : pos;
    }

    @SuppressWarnings("unchecked")
    public List<T> collect(int pos) {
        Region region = findRegion(pos);
        return region == null ? Collections.emptyList() : (List<T>) region.value;
    }

    @SuppressWarnings("unchecked")
    public <C extends Collection<T>> C collectTo(int pos, C target) {
        Region region = findRegion(pos);
        if(region != null) target.addAll((Collection<T>) region.value);
        return target;
    }

    @Nonnull
    public Iterator<Region> iterator() {
        return new ArrayIterator<>(data, 0, arrSize);
    }

    public Region[] dataArray() {
        return data;
    }

    /**
     *  The <i>insertion point</i> is defined as the point at which the
     *         key would be inserted into the array: the index of the first
     *         element in the range greater than the key,
     *         or <tt>toIndex</tt> if all
     *         elements in the range are less than the specified key.
     */
    private void insertAt(int index, Region data) {
        ensureCapacity(arrSize + 1);
        final Region[] data1 = this.data;
        if(arrSize - index > 0)
            System.arraycopy(data1, index, data1, index + 1, arrSize - index);
        data1[index] = data;
        arrSize++;
    }

    private void removeId(int index) {
        Region[] data1 = this.data;
        if(arrSize - index - 1 > 0)
            System.arraycopy(data1, index + 1, data1, index, arrSize - index - 1);
        data1[--arrSize] = null;
        if(arrSize > 0) data1[arrSize - 1].value.clear();
    }

    void ensureCapacity(int cap) {
        if(data.length < cap) {
            Region[] newArr = new Region[cap + 10];
            if(arrSize > 0)
                System.arraycopy(data, 0, newArr, 0, arrSize);
            data = newArr;
        }
    }

    public String toString() {
        return "Unioner" + ArrayUtil.toString(data, 0, arrSize);
    }

    /**
     * 两个方法的调用最好能常数时间
     */
    public interface Range {
        long startPos();
        long endPos();
    }

    public static final class Wrap<T1> implements Range {
        public int s, e;
        public T1 sth;

        public Wrap(T1 sth, int s, int e) {
            this.sth = sth;
            this.s = s;
            this.e = e;
        }

        @Override
        public long startPos() {
            return s;
        }

        @Override
        public long endPos() {
            return e;
        }

        @Override
        public String toString() {
            return "Cross{" + s + " => " + e + '}' + sth.toString();
        }
    }

    public static final class Region {
        List<Range> value;
        Point node;

        public Region(boolean care) {
            value = care ? new SimpleList<>() : Collections.emptyList();
        }

        void init(boolean care) {
            if ((value == Collections.EMPTY_LIST) == care) {
                value = care ? new SimpleList<>() : Collections.emptyList();
            }
        }

        public Point node() {
            return node;
        }

        public long pos() {
            return node.pos();
        }

        @Override
        public String toString() {
            return "Region{" + "value=" + value + ", node=" + node + '}';
        }

        @SuppressWarnings("unchecked")
        public <T extends Range> List<T> value() {
            return (List<T>) value;
        }
    }

    public static final class Point {
        Range owner;
        boolean end;
        Point next;

        public Point(Range data, boolean end) {
            this.end = end;
            owner = data;
        }

        long pos() {
            return end ? owner.endPos() : owner.startPos();
        }

        public boolean end() {
            return end;
        }

        @Override
        public String toString() {
            return "At(" + pos() + ")";
        }

        @SuppressWarnings("unchecked")
        public <T extends Range> T owner() {
            return (T) owner;
        }

        public Point next() {
            return next;
        }
    }
}