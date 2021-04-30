/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 7
 * Author: R__
 * Filename: java
 */
package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

public class WeakHashSet<K> implements Set<K>, CItrMap<WeakHashSet.Entry> {

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    protected static class Entry extends WeakReference<Object> implements EntryIterable<Entry> {
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
            Object ano = get();
            return ano == null ? 0 : ano.hashCode();
        }

        private int index;
        private Entry next;

        @Override
        public Entry nextEntry() {
            return next;
        }
    }

    protected Entry[] entries;
    protected int size = 0;

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

    void afterPut(K key) {
    }

    public void resize() {
        Entry[] newEntries = new Entry[length];
        Entry entry;
        Entry next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = entries[i];
            entries[i] = null;
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.hashCode());
                Entry old = newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = old;
                entry.index = newKey;
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

        int index = indexFor(key);
        Entry result;
        if (this.entries == null)
            this.entries = new Entry[length];
        result = entries[index];

        if (size > length * 0.8f) {
            length <<= 1;
            resize();
        }

        if (result == null) {
            (entries[index] = new Entry(queue, key)).index = index;
            size++;
            afterPut(key);
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
        (result.next = new Entry(queue, key)).index = index;
        size++;
        afterPut(key);
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
        int index = indexFor(key);
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

        int index = indexFor(key);
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

    int indexFor(@Nonnull Object obj) {
        return obj.hashCode() & (length - 1);
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

    private void removeClearedEntry() {
        Entry entry;
        while ((entry = (Entry) queue.poll()) != null) {
            Entry curr = entries[entry.index];
            Entry prev = null;
            while (curr != entry) {
                prev = curr;
                if (curr == null)
                    break;
                curr = curr.next;
            }

            if (prev == null) {
                entries[entry.index] = null;
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