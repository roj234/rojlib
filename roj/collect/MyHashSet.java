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
import java.util.*;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.NOT_USING;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/15 21:33
 */
public class MyHashSet<K> implements Set<K>, CItrMap<MyHashSet.Entry<K>>, FindSet<K> {
    public static class Entry<K> implements EntryIterable<Entry<K>> {
        public K k;

        protected Entry(K k) {
            this.k = k;
        }

        public Entry<K> next;

        @Override
        public Entry<K> nextEntry() {
            return next;
        }
    }

    protected Entry<?>[] entries;
    protected int size = 0;

    protected int length = 2;

    float loadFactor = 0.8f;

    public MyHashSet() {
        this(16);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public MyHashSet(K... arr) {
        ensureCapacity(arr.length);
        this.addAll(arr);
    }

    public MyHashSet(Iterable<K> list) {
        for (K k : list) {
            add(k);
        }
    }

    public MyHashSet(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    public MyHashSet(int size) {
        ensureCapacity(size);
    }

    public MyHashSet(Collection<K> list) {
        ensureCapacity(list.size());
        this.addAll(list);
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);
        if (this.entries != null)
            resize();
    }

    @Nonnull
    public Iterator<K> iterator() {
        return isEmpty() ? Collections.emptyIterator() : new SetItr<>(this);
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        return ArrayIterator.byIterator(iterator(), size());
    }

    @Nonnull
    @Override
    public <T> T[] toArray(@Nonnull T[] ts) {
        return ArrayIterator.byIterator(Helpers.cast(iterator()), ts);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    @Override
    public K find(K k) {
        Entry<K> entry = getEntry(k);
        return entry == null ? k : entry.k;
    }

    public K intern(K k) {
        Entry<K> entry = getOrCreateEntry(k);
        if(entry.k == NOT_USING) {
            entry.k = k;
            size++;
        }
        return entry.k;
    }

    @Override
    public void removeEntry0(Entry<K> kEntry) {
        remove(kEntry.k);
    }

    @SuppressWarnings("unchecked")
    public void addAll(MyHashSet<K> otherSet) {
        for (int i = 0; i < otherSet.length; i++) {
            Entry<K> entry = (Entry<K>) otherSet.entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                this.add(entry.k);
                entry = entry.next;
            }
        }
    }

    public void addAll(K[] arr) {
        for (K k : arr) {
            add(k);
        }
    }

    public void deduplicate(Collection<K> otherSet) {
        for (K k : otherSet) {
            if (this.add(k)) {
                otherSet.remove(k);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void deduplicate(MyHashSet<K> otherSet) {
        for (Entry<?> value : otherSet.entries) {
            Entry<K> entry = (Entry<K>) value;
            if (entry == null)
                continue;
            while (entry != null) {
                if (this.add(entry.k)) {
                    otherSet.remove(entry.k);
                }
                entry = entry.next;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        //System.err.println("扩容为: "+ DELIM);
        Entry<?>[] newEntries = new Entry<?>[length];
        Entry<K> entry;
        Entry<K> next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = (Entry<K>) entries[i];
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.k);
                Entry<K> old = (Entry<K>) newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = old;
                entry = next;
            }
        }

        this.entries = newEntries;

    }

    public boolean add(K key) {
        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Entry<K> entry = getOrCreateEntry(key);
        if (NOT_USING.equals(entry.k)) {
            entry.k = key;
            size++;
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        for (Object o : collection) {
            if (!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends K> collection) {
        boolean a = false;
        for (K k : collection) {
            a |= this.add(k);
        }
        return a;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> collection) {
        boolean a = false;
        for (K k : this) {
            if (!collection.contains(k))
                a |= remove(k);
        }
        return a;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> collection) {
        boolean k = false;
        for (Object o : collection) {
            k |= remove(o);
        }
        return k;
    }

    @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        K id = (K) o;

        Entry<K> prevEntry = null;
        Entry<K> toRemove = null;
        {
            Entry<K> entry = getEntryFirst(id, false);
            while (entry != null) {
                if (Objects.equals(o, entry.k)) {
                    toRemove = entry;
                    break;
                }
                prevEntry = entry;
                entry = entry.next;
            }
        }

        if (toRemove == null)
            return false;

        this.size--;

        if (prevEntry != null) {
            prevEntry.next = toRemove.next;
        } else {
            this.entries[indexFor(id)] = toRemove.next;
        }

        putRemovedEntry(toRemove);

        return true;
    }

    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        Entry<K> entry = getEntry((K) o);
        return entry != null;
    }

    protected Entry<K> notUsing = null;
    protected int removedLength = 0;

    protected Entry<K> getCachedEntry(K id) {
        Entry<K> cached = this.notUsing;
        if (cached != null) {
            cached.k = id;
            this.notUsing = cached.next;
            cached.next = null;
            removedLength--;
            return cached;
        }

        return new Entry<>(id);
    }

    protected void putRemovedEntry(Entry<K> entry) {
        if (notUsing != null && removedLength > MAX_NOT_USING) {
            return;
        }
        entry.k = null;
        entry.next = notUsing;
        removedLength++;
        notUsing = entry;
    }

    public Entry<K> getEntry(K id) {
        Entry<K> entry = getEntryFirst(id, false);
        while (entry != null) {
            if (Objects.equals(id, entry.k))
                return entry;
            entry = entry.next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Entry<K> getOrCreateEntry(K id) {
        Entry<K> entry = getEntryFirst(id, true);
        if (entry.k == NOT_USING)
            return entry;
        while (true) {
            if (Objects.equals(id, entry.k))
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;
        }
        Entry<K> firstUnused = getCachedEntry((K) NOT_USING);
        entry.next = firstUnused;
        return firstUnused;
    }

    protected int indexFor(K id) {
        int v;
        return id == null ? 0 : ((v = id.hashCode()) ^ (v >>> 16)) & (length - 1);
    }

    @SuppressWarnings("unchecked")
    public Entry<K> getEntryFirst(K id, boolean create) {
        int i = indexFor(id);
        if (entries == null) {
            if (!create)
                return null;
            entries = new Entry<?>[length];
        }
        Entry<K> entry;
        if ((entry = (Entry<K>) entries[i]) == null) {
            if (!create)
                return null;
            entries[i] = entry = getCachedEntry((K) NOT_USING);
        }
        return entry;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder("MyHashSet").append('{');
        for (K key : this) {
            sb.append(key).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void slowClear() {
        if (size == 0)
            return;
        size = 0;
        if (entries != null) {
            length = 16;
            entries = null;
        }
        if(removedLength != 0) {
            removedLength = 0;
            notUsing = null;
        }
    }

    public void clear() {
        if (size == 0)
            return;
        size = 0;
        if (entries != null)
            if (notUsing == null || removedLength < MAX_NOT_USING) {
                for (int i = 0; i < length; i++) {
                    if (entries[i] != null) {
                        putRemovedEntry(Helpers.cast(entries[i]));
                        entries[i] = null;
                    }
                }
            } else Arrays.fill(entries, null);
    }

    static class SetItr<K> extends MapItr<Entry<K>> implements Iterator<K> {
        public SetItr(MyHashSet<K> map) {
            super(map.entries, map);
        }

        public K next() {
            return nextT().k;
        }
    }
}