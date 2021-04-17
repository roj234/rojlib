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
package roj.collect;

import static roj.collect.IntMap.NOT_USING;

/**
 * A Least Frequency Used cache implement in O(1) time complexity
 *
 * @author Roj233
 * @version 1.0
 * @since 2021/9/9 23:17
 */
public class LFUCache<K, V> extends MyHashMap<K, V> {
    public static final class Entry<K, V> extends MyHashMap.Entry<K, V> {
        FreqList owner;
        Entry<K, V> lfuPrev, lfuNext;
        public Entry(K k, V v) {
            super(k, v);
        }
    }

    protected static final class FreqList {
        int freq;
        FreqList prev, next;
        Entry<?, ?> lfuNext;
    }

    public final int maximumCapacity, removeAtOnce;
    private final FreqList head;
    private int recycleSize;
    private FreqList recycleBin;

    public LFUCache(int maximumCapacity) {
        this.maximumCapacity = maximumCapacity;
        this.removeAtOnce = 16;
        this.head = new FreqList();
        this.head.freq = 1;
    }

    @Override
    protected final MyHashMap.Entry<K, V> createEntry(K id) {
        return new Entry<>(id, null);
    }

    @Override
    public final MyHashMap.Entry<K, V> getEntry(K id) {
        MyHashMap.Entry<K, V> entry = super.getEntry(id);
        if (entry != null && entry.v != NOT_USING) access((Entry<K, V>) entry);
        return entry;
    }

    @Override
    protected final MyHashMap.Entry<K, V> getCachedEntry(K id) {
        if(size == maximumCapacity) {
            evict(removeAtOnce);
        }
        Entry<K, V> entry = (Entry<K, V>) super.getCachedEntry(id);
        entry.owner = head;
        return entry;
    }

    @Override
    protected final void putRemovedEntry(MyHashMap.Entry<K, V> entry) {
        super.putRemovedEntry(entry);
        Entry<K, V> entry1 = (Entry<K, V>) entry;
        if(entry1.lfuPrev == null) {
            FreqList fl = entry1.owner;
            if((fl.lfuNext = entry1.lfuNext) == null) {
                if(fl.prev != null) { // do not remove head (1)
                    rcFreq(fl);
                }
            }
        } else {
            (entry1.lfuPrev.lfuNext = entry1.lfuNext)
                    .lfuPrev = entry1.lfuPrev;
        }
    }

    @Override
    public final void clear() {
        head.next = null;
        head.lfuNext = null;
        recycleSize = 0;
        recycleBin = null;
    }

    public final void evict(int amount) {
        FreqList cur = head;
        while (cur != null && amount > 0) {
            Entry<?, ?> ent = cur.lfuNext;
            while (ent != null && amount-- > 0) {
                remove(ent.k);
                ent = ent.lfuNext;
            }

            if((cur.lfuNext = ent) == null) {
                if(cur.prev != null) { // do not remove head (1)
                    FreqList t = cur.next;
                    rcFreq(cur);
                    cur = t;
                }
            }
            cur = cur.next;
        }
    }

    @SuppressWarnings("unchecked")
    public final void access(Entry<K, V> entry) {
        int nextFreq = entry.owner.freq + 1;
        FreqList cur = entry.owner;
        while (cur.freq < nextFreq) {
            if(cur.next == null) {
                FreqList one = rtFreq();
                one.freq = nextFreq;
                cur.next = one;
                one.prev = cur;
                break;
            }
            cur = cur.next;
        }
        if(cur.freq > nextFreq) {
            FreqList one = rtFreq();
            one.freq = nextFreq;
            one.prev = cur.prev;
            one.next = cur;
            cur.prev = one;
        }

        if(entry.lfuPrev != null) {
            (entry.lfuPrev.lfuNext = entry.lfuNext).lfuPrev = entry.lfuPrev;
        } else {
            FreqList o = entry.owner;
            if((o.lfuNext = entry.lfuNext) == null) {
                if(o.prev != null) { // do not remove head (1)
                    rcFreq(o);
                }
            }
        }
        entry.owner = cur;

        entry.lfuPrev = null;
        (entry.lfuNext = (Entry<K, V>) cur.lfuNext)
                .lfuPrev = entry;

        cur.lfuNext = entry;
    }

    private void rcFreq(FreqList o) {
        o.prev.next = o.next;
        o.next.prev = o.prev;
        if(recycleSize < 16) {
            o.next = recycleBin;
            recycleBin.prev = o;
            o.prev = null;
            recycleBin = o;
            recycleSize++;
        }
    }

    private FreqList rtFreq() {
        if(recycleSize > 0) {
            FreqList rt = recycleBin;
            recycleBin = rt.next;
            recycleSize--;
            rt.prev = rt.next = null;
            return rt;
        }
        return new FreqList();
    }
}
