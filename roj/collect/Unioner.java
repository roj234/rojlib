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

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.collect.Unioner.Range;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Union Finder
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/27 18:20
 */
public class Unioner<T extends Range> implements Iterable<Unioner.Region> {
    private static final Region[] EMPTY_DATA = new Region[0];

    public Unioner() {
        data = EMPTY_DATA;
    }

    public Unioner(int initialCapacity) {
        data = new Region[initialCapacity << 1];
    }

    Region[] data;
    int size, arrSize;

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

    public void reuseClear() {
        for (int i = 0; i < arrSize; i++) {
            data[i] = null;
        }
        arrSize = size = 0;
    }

    public void clear() {
        data = EMPTY_DATA;
        arrSize = size = 0;
    }

    public boolean add(T t) {
        long sp = t.startPos();
        if(sp >= t.endPos())
            throw new IndexOutOfBoundsException("start >= end: " + t);

        int nearest = binarySearch(sp);
        Point start = new Point(t, false);
        if(nearest >= 0) {
            // already contains
            Region h = this.data[nearest];
            if(h == null) {
                this.data[nearest] = h = new Region();
                if(nearest > 0) {
                    h.value.addAll(this.data[nearest - 1].value);
                }
            }
            Point point = h.node;

            while (true) {
                if(t.equals(point.owner))
                    return false;
                if(point.next == null) {
                    point.next = start;
                    break;
                }
                point = point.next;
            }
        } else {
            Region h = new Region();
            h.node = start;
            if((nearest = -nearest - 1) > 0) {
                h.value.addAll(this.data[nearest - 1].value);
            }
            insertAt(nearest, h);
        }

        int endPos = binarySearch(t.endPos());
        Point end = new Point(t, true);
        if(endPos >= 0) {
            Region h = this.data[endPos];
            if(h == null) {
                this.data[endPos] = h = new Region();
                if(endPos > 0) {
                    h.value.addAll(this.data[endPos - 1].value);
                }
            }
            Point point = h.node;

            while (point.next != null) {
                // Don't need to check twice
                point = point.next;
            }
            point.next = end;
        } else {
            Region h = new Region();
            h.node = end;
            if((endPos = -endPos - 1) > 0) {
                h.value.addAll(this.data[endPos - 1].value);
            }
            insertAt(endPos, h);
        }

        for (; nearest < endPos; nearest++) {
            data[nearest].value.add(t);
        }

        size++;

        return true;
    }

    public boolean remove(T t) {
        long startPos = t.startPos();
        if(startPos >= t.endPos())
            throw new IndexOutOfBoundsException("start >= end");

        int nearest = binarySearch(startPos);

        k:
        if(nearest >= 0) {
            Point prev = null;
            Point point = this.data[nearest].node;
            while (point != null) {
                if(t.equals(point.owner)) {
                    if(prev == null) {
                        if(point.next == null)
                            removeId(nearest);
                        else
                            this.data[nearest].node = point.next;
                    } else {
                        prev.next = point.next;
                    }
                    break k;
                }
                prev = point;
                point = point.next;
            }
            return false;
        } else {
            return false;
        }

        int endPos = binarySearch(t.endPos());
        l:
        if(endPos >= 0) {
            Point prev = null;
            Point point = this.data[endPos].node;
            while (point != null) {
                if(t.equals(point.owner)) {
                    if(prev == null) {
                        if(point.next == null)
                            removeId(endPos);
                        else
                            this.data[endPos].node = point.next;
                    } else {
                        prev.next = point.next;
                    }
                    break l;
                }
                prev = point;
                point = point.next;
            }
            throw new IllegalStateException("Unable to find endPos for " + t.endPos());
        } else {
            System.out.println(this);
            throw new IllegalStateException("Unable to find endPos for " + t.endPos());
        }

        if(data[endPos] == null)
            endPos--;

        for (; nearest < endPos; nearest++) {
            data[nearest].value.remove(t);
        }

        size--;

        return true;
    }

    public int size() {
        return size;
    }

    public int arraySize() {
        return arrSize;
    }

    public Region findRegion(int pos) {
        pos = binarySearch(pos);
        if(pos < 0) {
            // array id now
            if((pos = -pos - 2) < 0) return null;
        }
        return data[pos];
    }

    @SuppressWarnings("unchecked")
    @Internal
    public List<T> i_collect(int pos) {
        Region region = findRegion(pos);
        return region == null ? Collections.emptyList() : (List<T>) region.value;
    }

    @SuppressWarnings("unchecked")
    public List<T> collect(int pos) {
        Region region = findRegion(pos);
        return region == null ? Collections.emptyList() : Collections.unmodifiableList((List<T>) region.value);
    }

    @SuppressWarnings("unchecked")
    public <COLLECTION extends Collection<T>> COLLECTION collect(int pos, COLLECTION target) {
        Region region = findRegion(pos);
        if(region != null)
            target.addAll((Collection<T>) region.value);
        return target;
    }

    @Nonnull
    public Iterator<Region> iterator() {
        return new ArrayIterator<>(data, 0, arrSize);
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
        // [0, 1, 2] insert A at 1
        // [0, A, 1, 2]
        // copy i to 2, length = 2
        if(arrSize - index > 0)
            System.arraycopy(data1, index, data1, index + 1, arrSize - index);
        //else if(data1[arrSize] != null)
        //    data1[arrSize].region = new ArrayList<>();
        data1[index] = data;
        arrSize++;
    }

    private void removeId(int index) {
        Region[] data1 = this.data;
        // [0, 1, 2, 3] remove 1
        // [0, 2, 3]
        // copy 2 to 1 len = 2
        if(arrSize - index - 1 > 0)
            System.arraycopy(data1, index + 1, data1, index, arrSize - index - 1);
        data1[--arrSize] = null;
        if(arrSize > 0)
            data1[arrSize - 1].value.clear();
    }

    void ensureCapacity(int cap) {
        if(data.length < cap) {
            Region[] newArr = new Region[cap + 8];
            if(arrSize > 0)
                System.arraycopy(data, 0, newArr, 0, arrSize);
            data = newArr;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Unioner[");
        for (int i = 0; i < arrSize; i++) {
            Unioner.Point d = data[i].node;
            sb.append("\n  Values: ").append(data[i].value).append("\n  Points: {\n   ");
            if(d != null) {
                while (d != null) {
                    sb.append(d.owner).append('-').append(d.pos()).append(d.end ? ",E\n   " : ",S\n   ");
                    d = d.next;
                }
                sb.setLength(sb.length() - 4);
            }
            sb.append("}");
        }
        return sb.append(']').toString();
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
        final ArrayList<Range> value = new ArrayList<>();
        Point node;

        public Point node() {
            return node;
        }

        @Override
        public String toString() {
            return "Region{" + "value=" + value + ", node=" + node + '}';
        }

        @SuppressWarnings("unchecked")
        public <T extends Range> List<T> value() {
            return (List<T>) Collections.unmodifiableList(value);
        }

        @SuppressWarnings("unchecked")
        @Internal
        public <T extends Range> List<T> i_value() {
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

        public long pos() {
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