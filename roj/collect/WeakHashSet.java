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

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/16 14:34
 */
public class WeakHashSet<K> implements Set<K>, MapLike<WeakHashSet.Entry> {

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    protected static class Entry extends WeakReference<Object> implements MapLikeEntry<Entry> {
        public Entry(ReferenceQueue<Object> queue, Object referent) {
            super(referent, queue);
        }

        @Override
        public boolean equals(Object obj) {
            Object ano = get();
            return ano == null ? obj == null : ano.equals(obj);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        int hash;
        Entry next;

        @Override
        public Entry nextEntry() {
            return next;
        }
    }

    protected Entry[] entries;
    protected int size = 0, gc_ed;

    int length = 2;

    boolean hasNull = false;

    public WeakHashSet() {
        this(16);
    }

    /**
     * @param size 初始化大小
     */
    public WeakHashSet(int size) {
        ensureCapacity(size);
    }

    public void ensureCapacity(int size) {
        if (size <= 0) throw new NegativeArraySizeException(String.valueOf(size));
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);

        if (entries != null)
            resize();
    }

    private static class SetItr<K> extends MapItr<Entry> implements Iterator<K> {
        public SetItr(WeakHashSet<K> map) {
            super(map.entries, map);
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        @SuppressWarnings("unchecked")
        public K next() {
            return (K) nextT().get();
        }
    }


    @Override
    public int size() {
        removeClearedEntry();
        return hasNull ? size + 1 : size;
    }

    @Override
    public void removeEntry0(Entry entry) {
        remove(entry.get());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    public void resize() {
        removeClearedEntry();

        Entry[] newEntries = new Entry[length];
        Entry entry;
        Entry next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = entries[i];
            entries[i] = null;
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.hash);
                Entry old = newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = old;
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    @Override
    public boolean add(K key) {
        if (key == null) {
            if (!this.hasNull) {
                this.hasNull = true;
                return true;
            }
            return false;
        }

        removeClearedEntry();

        int hash = key.hashCode();
        int index = indexFor(hash);
        Entry result;
        if (this.entries == null)
            this.entries = new Entry[length];
        result = entries[index];

        if (size > length * 0.8f) {
            length <<= 1;
            resize();
        }

        if (result == null) {
            (entries[index] = new Entry(queue, key)).hash = hash;
            size++;
            return true;
        }
        while (true) {
            if (result.equals(key)) {
                return false;
            }
            if (result.next == null)
                break;
            result = result.next;
        }
        (result.next = new Entry(queue, key)).hash = hash;
        size++;
        return true;
    }

    @Override
    public boolean remove(Object key) {
        if (key == null) {
            if (this.hasNull) {
                this.hasNull = false;
                return true;
            } else {
                return false;
            }
        }

        removeClearedEntry();

        if (this.entries == null)
            return false;
        int index = indexFor(key.hashCode());
        Entry curr = entries[index];
        Entry prev = null;
        while (curr != null) {
            if (Objects.equals(curr.get(), key)) {
                if (prev == null)
                    entries[index] = null;
                else {
                    prev.next = curr.next;
                }
                return true;
            }
            prev = curr;
            curr = curr.next;
        }

        return false;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> collection) {
        for (Object o : collection) {
            if (!this.contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends K> collection) {
        for (K k : collection) {
            this.add(k);
        }
        return true;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> collection) {
        return removeIf(collection::contains);
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> collection) {
        return removeIf(k -> !collection.contains(k));
    }

    @Override
    public boolean contains(Object key) {
        if (key == null)
            return this.hasNull;
        if (entries == null) return false;

        removeClearedEntry();

        int index = indexFor(key.hashCode());
        Entry curr = entries[index];
        while (curr != null) {
            if (Objects.equals(curr.get(), key)) {
                return true;
            }
            curr = curr.next;
        }
        return false;
    }

    @Nonnull
    @Override
    public Iterator<K> iterator() {
        // 这不香吗
        return isEmpty() ? Collections.emptyIterator() : new SetItr<>(this);
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        return ArrayIterator.byIterator(iterator(), size);
    }

    @Nonnull
    @Override
    public <T> T[] toArray(@Nonnull T[] a) {
        return ArrayIterator.byIterator(Helpers.cast(iterator()), a);
    }

    int indexFor(int obj) {
        return obj & (length - 1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WeakHashSet").append('{');
        for (K key : this) {
            sb.append(key).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void removeClearedEntry() {
        Entry entry;
        o:
        while ((entry = (Entry) queue.poll()) != null) {
            gc_ed++;

            Entry curr = entries[indexFor(entry.hash)];
            Entry prev = null;
            while (curr != entry) {
                if (curr == null) continue o;

                prev = curr;
                curr = curr.next;
            }

            if (prev == null) {
                entries[indexFor(entry.hash)] = null;
            } else {
                prev.next = curr.next;
            }
            size--;
        }
    }

    @Override
    public void clear() {
        size = 0;
        while (queue.poll() != null) ;

        this.hasNull = false;
        if (this.entries != null)
            Arrays.fill(entries, null);

        while (queue.poll() != null) ;
    }

    public boolean containsNull() {
        return this.hasNull;
    }
}