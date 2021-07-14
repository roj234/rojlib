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

import java.util.Collection;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.function.Predicate;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/4 14:32java
 */
public class TimedHashMap<K, V> extends MyHashMap<K, V> {
    protected long timeout, lastCheck;

    protected boolean selfRemove, update;

    public TimedHashMap(int timeOut) {
        this.timeout = (update = timeOut < 0) ? -timeOut : timeOut;
    }

    LinkedList<TimedEntry<K, V>> list = new LinkedList<>();

    public void retainOutDated(Collection<K> kholder, Collection<V> vholder, Predicate<Entry<K, V>> kPredicate, int max) {
        long curr = System.currentTimeMillis();

        selfRemove = true;

        if (!this.list.isEmpty())
            for (ListIterator<TimedEntry<K, V>> iterator = this.list.listIterator(this.list.size() - 1); iterator.hasPrevious(); ) {
                TimedEntry<K, V> entry = iterator.previous();
                if (curr - entry.timestamp >= timeout || max-- <= 0) {
                    if(kPredicate.test(entry)) {
                        kholder.add(entry.k);
                        vholder.add(entry.v);
                    }
                    remove(entry.k);
                    iterator.remove();
                } else {
                    break;
                }
            }

        lastCheck = curr;

        selfRemove = false;
    }

    public static class TimedEntry<K, V> extends MyHashMap.Entry<K, V> {
        protected long timestamp = System.currentTimeMillis();

        protected TimedEntry(K k, V v) {
            super(k, v);
        }
    }

    @Override
    protected void putRemovedEntry(Entry<K, V> entry) {
    }

    @Override
    void afterRemove(Entry<K, V> entry) {
        if (!selfRemove)
            throw new IllegalStateException("There is not remove!");
    }

    @Override
    void afterPut(Entry<K, V> entry) {
        list.add(0, (TimedEntry<K, V>) entry);
    }

    @Override
    protected Entry<K, V> createEntry(K id) {
        return new TimedEntry<>(id, null);
    }

    @Override
    public Entry<K, V> getEntry(K id) {
        Entry<K, V> entry = super.getEntry(id);
        if (entry == null)
            return null;
        TimedEntry<?, ?> entry1 = (TimedEntry<?, ?>) entry;
        long t = System.currentTimeMillis() - entry1.timestamp;
        if (t >= timeout) {
            if (entry.v != IntMap.NOT_USING) {
                selfRemove = true;
                remove(entry.k);
                selfRemove = false;
            }
            return null;
        }
        if (update)
            entry1.timestamp += t;
        return entry;
    }

    public void clearOutdatedEntry() {
        long curr = System.currentTimeMillis();

        selfRemove = true;

        if (!this.list.isEmpty())
            for (ListIterator<TimedEntry<K, V>> iterator = this.list.listIterator(this.list.size() - 1); iterator.hasPrevious(); ) {
                TimedEntry<K, V> entry = iterator.previous();
                if (curr - entry.timestamp >= timeout) {
                    remove(entry.k);
                    iterator.remove();
                } else {
                    break;
                }
            }

        lastCheck = curr;

        selfRemove = false;
    }
}
