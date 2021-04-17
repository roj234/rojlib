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
import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;

import static roj.collect.IntMap.NOT_USING;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/6/15 21:33
 */
public class MyHashSet<K> implements Set<K>, FindSet<K> {
    @Internal
    protected static final class Entry {
        public Object k, next;
        Entry(Object k) {
            this.k = k;
        }

        @Override
        public String toString() {
            return "{" + k + '}';
        }
    }

    protected boolean  hasNull;
    protected Object[] entries;
    protected int size = 0, length = 2;

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

    public MyHashSet(Iterable<? extends K> list) {
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

    public MyHashSet(Collection<? extends K> list) {
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
    @SuppressWarnings("unchecked")
    public K find(K k) {
        Object entry = getEntry(k);
        return entry == NOT_USING ? k : (K) entry;
    }

    @SuppressWarnings("unchecked")
    public K intern(K k) {
        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Object entry = getOrCreateEntry(k);
        if(entry == NOT_USING) {
            entry = k;
            size++;
        }
        return (K) entry;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Object[] newEntries = new Object[length];
        Entry entry;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            Object obj = entries[i];
            while (obj instanceof Entry) {
                entry = (Entry) obj;
                int newKey = indexFor((K) entry.k);

                obj = entry.next;

                Object old = newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = old;
            }
            if (obj == null) continue;
            int newKey = indexFor((K) obj);
            Object old = newEntries[newKey];
            if (old == null) {
                newEntries[newKey] = obj;
            } else if (old instanceof Entry) {
                entry = (Entry) old;
                if (entry.next != null)  {
                    Entry entry1 = new Entry(obj);
                    entry1.next = entry;
                    newEntries[newKey] = entry1;
                } else {
                    entry.next = obj;
                }
            } else {
                Entry entry1 = new Entry(obj);
                entry1.next = old;
                newEntries[newKey] = entry1;
            }
        }

        this.entries = newEntries;
    }

    public boolean add(K key) {
        if (key == null) {
            if (!hasNull) {
                hasNull = true;
                size++;
                return true;
            }
            return false;
        }

        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Object entry = getOrCreateEntry(key);
        if (NOT_USING == entry) {
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

    public boolean addAll(K[] collection) {
        boolean a = false;
        for (K k : collection) {
            a |= this.add(k);
        }
        return a;
    }

    public void deduplicate(Collection<K> otherSet) {
        for (K k : otherSet) {
            if (!this.add(k)) {
                otherSet.remove(k);
            }
        }
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
        if (o == null) {
            if (hasNull) {
                hasNull = false;
                size--;
                return true;
            }
            return false;
        }
        if (entries == null) {
            return false;
        }
        K id = (K) o;

        int i = indexFor(id);
        Entry prev = null;
        Object obj = entries[i];

        chk:
        {
            while (obj instanceof Entry) {
                Entry curr = (Entry) obj;
                if (Objects.equals(id, curr.k)) break chk;
                prev = curr;
                obj = prev.next;
            }

            if (obj == null || !Objects.equals(id, obj))
                return false;
        }

        this.size--;

        Object next = obj instanceof Entry ? ((Entry) obj).next : null;
        if (prev != null) {
            prev.next = next;
        } else {
            this.entries[i] = next;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        if (o == null) return hasNull;
        return getEntry((K) o) != NOT_USING;
    }

    public Object getEntry(K id) {
        if (entries == null) {
            return NOT_USING;
        }
        Object obj = entries[indexFor(id)];
        while (obj instanceof Entry) {
            Entry prev = (Entry) obj;
            if (Objects.equals(id, prev.k))
                return prev.k;
            obj = prev.next;
        }
        if (Objects.equals(id, obj))
            return obj;
        return NOT_USING;
    }

    public Object getOrCreateEntry(K id) {
        int i = indexFor(id);
        if (entries == null) {
            entries = new Object[length];
            entries[i] = id;
            return NOT_USING;
        }
        Object obj = entries[i];
        while (obj instanceof Entry) {
            Entry prev = (Entry) obj;
            if (Objects.equals(id, prev.k))
                return prev.k;
            if (prev.next == null) { // after resize()
                prev.next = id;
                return NOT_USING;
            }
            obj = prev.next;
        }
        if (Objects.equals(id, obj))
            return obj;

        Entry unused = new Entry(id);
        unused.next = entries[i];
        entries[i] = unused;
        return NOT_USING;
    }

    protected int indexFor(K id) {
        int v;
        return ((v = id.hashCode()) ^ (v >>> 16)) & (length - 1);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder().append('[');
        for (K key : this) {
            sb.append(key).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append(']').toString();
    }

    public void clear() {
        if (size == 0)
            return;
        size = 0;
        if (entries != null)
            Arrays.fill(entries, null);
    }

    static final class SetItr<K> extends AbstractIterator<K> {
        final MyHashSet<K> che;
        private Object entry;
        private int i;

        public SetItr(MyHashSet<K> map) {
            this.che = map;
            if (map.hasNull) {
                stage = AbstractIterator.CHECKED;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean computeNext() {
            while (true) {
                if (entry != null) {
                    if (entry instanceof Entry) {
                        Entry entry = (Entry) this.entry;
                        result = (K) entry.k;
                        this.entry = entry.next;
                    } else {
                        result = (K) entry;
                        entry = null;
                    }
                    return true;
                } else if (i < che.entries.length) {
                    this.entry = che.entries[i++];
                } else {
                    return false;
                }
            }
        }

        @Override
        protected void remove(K obj) {
            che.remove(obj);
        }
    }
}