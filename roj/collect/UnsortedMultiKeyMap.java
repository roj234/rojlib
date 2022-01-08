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

import roj.concurrent.OperationDone;
import roj.math.MutableBoolean;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.collect.IntMap.NOT_USING;

public class UnsortedMultiKeyMap<K, T, V> implements Map<List<K>, V> {
    public interface Keys<K, T> {
        /**
         * 建议保证返回的list中使用频率大的靠前
         * @param holder 装t的容器，空列表
         * @return holder, 非强制
         */
        List<T> getKeysFor(K key, List<T> holder);

        T getMostFamous(K k);
    }

    public static final class REntry<T, V> implements Iterable<REntry<T, V>> {
        T k;
        V v;

        REntry<T, V> next;

        @Nonnull
        REntry<?, ?>[] children;
        int size = 0;

        static final REntry<?, ?>[] EMPTY            = new REntry<?, ?>[0];
        static final int            INITIAL_CAPACITY = 1;
        static final float          LOAD_FACTOR      = 1f;

        @SuppressWarnings("unchecked")
        REntry(T k) {
            this.k = k;
            this.v = (V) NOT_USING;
            this.children = EMPTY;
        }

        REntry(REntry<T, V> entry) {
            if(entry.children.length > 0)
                this.children = new REntry<?, ?>[entry.children.length];
            else
                this.children = EMPTY;
            this.size = entry.size;
            this.k = entry.k;
            this.v = entry.v;
        }

        @SuppressWarnings("unchecked")
        void resize() {
            final REntry<?, ?>[] ch = this.children;
            REntry<?, ?>[] newEntries = new REntry<?, ?>[ch.length << 1];
            REntry<T, V> entry, next;

            int flg = newEntries.length - 1;

            int i = 0, j = ch.length;
            for (; i < j; i++) {
                entry = (REntry<T, V>) ch[i];
                while (entry != null) {
                    next = entry.next;

                    int newKey = entry.k.hashCode() & flg;

                    REntry<T, V> old = (REntry<T, V>) newEntries[newKey];

                    newEntries[newKey] = entry;
                    entry.next = old;
                    entry = next;
                }
            }

            this.children = newEntries;
        }

        @SuppressWarnings("unchecked")
        public REntry<T, V> getChild(T key) {
            REntry<?, ?>[] children = this.children;
            if(children.length == 0)
                return null;
            int hash = key.hashCode() & (children.length - 1);

            REntry<T, V> entry = (REntry<T, V>) children[hash];
            while (entry != null) {
                if (key.equals(entry.k)) {
                    return entry;
                }
                entry = entry.next;
            }

            return null;
        }

        @SuppressWarnings("unchecked")
        public boolean putChild(REntry<T, V> entryIn) {
            REntry<?, ?>[] children = this.children;
            if (children.length == 0) {
                this.children = children = new REntry<?, ?>[INITIAL_CAPACITY];
            } else if (size > ((children.length == INITIAL_CAPACITY && INITIAL_CAPACITY < 8) ? (INITIAL_CAPACITY + 1) : children.length * LOAD_FACTOR)) {
                resize();
                children = this.children;
            }

            REntry<T, V> entry;

            int hashId = entryIn.k.hashCode() & (children.length - 1);

            if ((entry = (REntry<T, V>) children[hashId]) == null) {
                children[hashId] = entryIn;
            } else {
                final T k = entryIn.k;
                while (true) {
                    if (k.equals(entry.k)) // has
                        return false;
                    if (entry.next == null)
                        break;
                    entry = entry.next;
                }
                entry.next = entryIn;
            }
            size++;

            return true;
        }

        @SuppressWarnings("unchecked")
        public boolean removeChild(T key) {
            final REntry<?, ?>[] children = this.children;
            if (children.length == 0) {
                return false;
            }

            REntry<T, V> entry, prev = null, toRemove = null;
            int hashId = key.hashCode() & (children.length - 1);

            entry = (REntry<T, V>) children[hashId];
            while (entry != null) {
                if (key.equals(entry.k)) {
                    toRemove = entry;
                    break;
                }
                prev = entry;
                entry = entry.next;
            }

            if(toRemove == null) {
                return false;
            }

            if (prev != null) {
                prev.next = toRemove.next;
            } else {
                this.children[hashId] = toRemove.next;
            }

            this.size--;

            return true;
        }

        public String toString() {
            return "{" + k + "=" + v + "}";
        }

        public void clear() {
            if (size == 0)
                return;
            size = 0;
            if(children.length > 16) {
                children = EMPTY;
            } else {
                Arrays.fill(children, null);
            }
        }

        int recursionSum() {
            int i = v != NOT_USING ? 1 : 0;
            if (size > 0) {
                for (REntry<T, V> value : this) {
                    i += value.recursionSum();
                }
            }
            return i;
        }

        public V getValue() {
            if (v == NOT_USING)
                throw new UnsupportedOperationException();
            return v;
        }

        public V setValue(V value) {
            if (v == NOT_USING)
                throw new UnsupportedOperationException();
            V ov = this.v;
            this.v = value;
            return ov;
        }

        @Nonnull
        @Override
        @SuppressWarnings("unchecked")
        public Iterator<REntry<T, V>> iterator() {
            if(children.length == 0)
                return Collections.emptyIterator();
            return new AbstractIterator<REntry<T, V>>() {
                REntry<T, V> entry;
                int i = 0;

                @Override
                public boolean computeNext() {
                    while (true) {
                        if (entry != null) {
                            result = entry;
                            entry = entry.next;
                            return true;
                        } else if (i < children.length) {
                            this.entry = (REntry<T, V>) children[i++];
                        } else {
                            return false;
                        }
                    }
                }
            };
        }

        public int copyFrom(REntry<T, V> node) {
            int v = 0;
            if(node.v != IntMap.NOT_USING && this.v == IntMap.NOT_USING) {
                this.v = node.v;
                v = 1;
            }

            for (REntry<T, V> entry : node) {
                REntry<T, V> sub = getChild(entry.k);
                if (sub == null) putChild(sub = new REntry<>(this));
                v += sub.copyFrom(entry);
            }
            return v;
        }
    }

    static final class Finder<T, V> {
        List<T>[] tHolder;
        REntry<T, V>[] pending, next;
        int[] source, source_next;

        Finder(int cap) {
            this.pending = Helpers.cast(new REntry<?, ?>[16]);
            this.next = Helpers.cast(new REntry<?, ?>[16]);
            this.tHolder = Helpers.cast(new List<?>[cap]);
            for (int i = 0; i < cap; i++) {
                this.tHolder[i] = new ArrayList<>(10);
            }
            this.source = new int[16];
            this.source_next = new int[16];
        }
    }

    public static <K,T,V> UnsortedMultiKeyMap<K, T, V> create(Keys<K,T> keys, int averageLength) {
        return new UnsortedMultiKeyMap<>(keys, averageLength);
    }

    final Keys<K, T> comparator;
    final int averageLength;

    @SuppressWarnings("rawtypes")
    static final AtomicReferenceFieldUpdater<UnsortedMultiKeyMap, Object> BufUpdater = AtomicReferenceFieldUpdater.newUpdater(UnsortedMultiKeyMap.class, Object.class, "buffer");
    volatile Object  buffer;

    REntry<T, V> root = new REntry<>((T)null);
    int size = 0;

    public UnsortedMultiKeyMap(Keys<K, T> comparator, int len) {
        this.comparator = comparator;
        this.averageLength = len;
    }

    public REntry<T, V> getEntry(List<K> s, int i, int len) {
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        REntry<T, V> entry = root;
        for (; i < len; i++) {
            entry = entry.getChild(comparator.getMostFamous(s.get(i)));
            if (entry == null) {
                return null;
            }
        }

        return entry.v != NOT_USING ? entry : null;
    }

    public REntry<T, V> getRoot() {
        return root;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public V put(List<K> keys, V e) {
        REntry<T, V> entry = root;
        REntry<T, V> prev;
        for (int i = 0; i < keys.size(); i++) {
            prev = entry;
            T t = comparator.getMostFamous(keys.get(i));
            entry = entry.getChild(t);
            if (entry == null) {
                prev.putChild(entry = new REntry<>(t));
            }
        }
        if (entry.v == NOT_USING) {
            size++;
            entry.v = null;
        }

        V v = entry.v;
        entry.v = e;
        return v;
    }

    public V putIntl(List<T> keys, V e) {
        REntry<T, V> entry = root;
        REntry<T, V> prev;
        for (int i = 0; i < keys.size(); i++) {
            prev = entry;
            T t = keys.get(i);
            entry = entry.getChild(t);
            if (entry == null) {
                prev.putChild(entry = new REntry<>(t));
            }
        }
        if (entry.v == NOT_USING) {
            size++;
            entry.v = null;
        }

        V v = entry.v;
        entry.v = e;
        return v;
    }

    public V ciaIntl(List<T> keys, Function<List<T>, V> fn) {
        REntry<T, V> entry = root;
        REntry<T, V> prev;
        for (int i = 0; i < keys.size(); i++) {
            prev = entry;
            T t = keys.get(i);
            entry = entry.getChild(t);
            if (entry == null) {
                prev.putChild(entry = new REntry<>(t));
            }
        }
        if (entry.v == NOT_USING) {
            size++;
            entry.v = fn.apply(keys);
        }

        return entry.v;
    }

    public void addTree(UnsortedMultiKeyMap<? extends List<K>, ? extends T, ? extends V> m) {
        size += root.copyFrom(Helpers.cast(m.root));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends List<K>, ? extends V> m) {
        if (m instanceof UnsortedMultiKeyMap) {
            addTree((UnsortedMultiKeyMap<? extends List<K>, ? extends T, ? extends V>) m);
            return;
        }
        for (Entry<? extends List<K>, ? extends V> entry : m.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object k) {
        List<K> s = (List<K>) k;
        return remove(s, 0, s.size());
    }

    public V remove(List<K> s, int len) {
        return remove(s, 0, s.size());
    }

    public V remove(List<K> s, int i, int len) {
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        SimpleList<REntry<T, V>> list = new SimpleList<>(Math.min(len, averageLength));

        REntry<T, V> entry = root;
        for (; i < len; i++) {
            entry = entry.getChild(comparator.getMostFamous(s.get(i)));
            if (entry == null)
                return null;

            list.add(entry);
        }

        if (entry.v == NOT_USING)
            return null;

        size--;

        if (!list.isEmpty()) {
            i = list.size;

            while (--i >= 0) {
                REntry<T, V> prev = list.get(i);

                if (prev.recursionSum() > 0) {
                    prev.removeChild(entry.k);
                    break;
                }
                entry = prev;
            }
            list.list = null;
        }

        return entry.v;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object i) {
        final List<K> s = (List<K>) i;
        return containsKey(s, 0, s.size());
    }

    public boolean containsKey(List<K> s, int len) {
        return containsKey(s, 0, len);
    }

    public boolean containsKey(List<K> s, int off, int len) {
        return getEntry(s, off, len) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        MutableBoolean mb = new MutableBoolean(false);
        try {
            forEach((k, v) -> {
                if (Objects.equals(value, v)) {
                    mb.set(true);
                    throw OperationDone.INSTANCE;
                }
            });
        } catch (OperationDone ignored) {
        }
        return mb.get();
    }

    public List<V> getMulti(List<K> s, int limit) {
        return getMulti(s, limit, new SimpleList<>());
    }

    @SuppressWarnings("unchecked")
    public List<V> getMulti(List<K> s, int limit, List<V> dest) {
        Finder<T, V> f = (Finder<T, V>) BufUpdater.getAndSet(this, null);
        if(f == null)
            f = new Finder<>(averageLength);

        List<T>[] tss = f.tHolder;
        for (int i = 0; i < s.size(); i++) {
            tss[i].clear();
            tss[i] = comparator.getKeysFor(s.get(i), tss[i]);
        }

        REntry<T, V>[] pend = f.pending;
        int pendUsed = 1;
        REntry<T, V>[] next = f.next;
        int nextUsed = 0;
        int[] src = f.source;
        int[] src_nx = f.source_next;

        pend[0] = root;
        src[0] = 0;

        int vx = 0, mask;
        int remain = s.size();
        int ae = 0, from;
        REntry<?, ?>[] children;
        REntry<T, V> eee;
        List<T> ts;
        T t;
        while (remain-- > 0) {
            for (int i = 0; i < s.size(); i++) {
                ts = tss[i];
                if((ae & (1L << i)) != 0) continue;
                for (int j = 0; j < pendUsed; j++) {
                    from = src[j];
                    if((from & (1L << i)) != 0) {
                        vx++;
                        continue;
                    }
                    from |= (1L << i);

                    children = pend[j].children;

                    mask = children.length - 1;

                    for (int k = 0; k < ts.size(); k++) {
                        t = ts.get(k);
                        eee = (REntry<T, V>) children[t.hashCode() & mask];
                        while (eee != null) {
                            if (t.equals(eee.k)) {
                                if(src_nx.length <= nextUsed) {
                                    int[] nx_1 = new int[nextUsed + 16];
                                    if(src_nx.length > 0)
                                        System.arraycopy(src_nx, 0, nx_1, 0, src_nx.length);
                                    src_nx = nx_1;
                                }
                                if(next.length <= nextUsed) {
                                    REntry<?, ?>[] nx_1 = new REntry<?, ?>[nextUsed + 16];
                                    System.arraycopy(next, 0, nx_1, 0, next.length);
                                    next = Helpers.cast(nx_1);
                                }
                                src_nx[nextUsed] = ((next[nextUsed++] = eee).children.length == 0 ? -1 : from);
                                break;
                            }
                            eee = eee.next;
                        }
                    }
                }
                if(vx == pendUsed) {
                    ae |= 1L << i;
                }
                vx = 0;
            }

            int[] tmp1 = src;
            src = src_nx;
            src_nx = tmp1;

            REntry<T, V>[] tmp2 = next;
            next = pend;
            pend = tmp2;

            int tmp = nextUsed;
            nextUsed = 0;
            pendUsed = tmp;

            if(tmp == 0) {
                break;
            }
        }

        f.pending = pend;
        f.next = next;
        f.source = src;
        f.source_next = src_nx;

        int i = 0;
        for (; i < pendUsed; i++) {
            REntry<T, V> entry = pend[i];
            if(entry.v != NOT_USING && dest.size() < limit) {
                dest.add(entry.v);
            }
        }
        for (; i < pend.length; i++) {
            if (pend[i] == null) {
                break;
            }
            pend[i] = null;
        }
        for (i = 0; i < next.length; i++) {
            if (next[i] == null) {
                break;
            }
            next[i] = null;
        }

        BufUpdater.compareAndSet(this, null, f);

        return dest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object id) {
        List<K> id1 = (List<K>) id;
        REntry<T, V> vs = getEntry(id1, 0, id1.size());
        return vs == null ? null : vs.v;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("UMKM").append('{');
        forEach1((k, v) -> sb.append(k).append('=').append(v).append(','));
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    @Override
    public void clear() {
        size = 0;
        root.clear();
    }

    /**
     * @see #forEach(BiConsumer)
     */
    @Nonnull
    @Override
    @Deprecated
    public Set<List<K>> keySet() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public Collection<V> values() {
        return new Values<>(this);
    }

    /**
     * @see #forEach(BiConsumer)
     */
    @Nonnull
    @Override
    @Deprecated
    public Set<Entry<List<K>, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

    public Iterator<REntry<T, V>> mapItr() {
        return new MapItr<>(root);
    }

    @Override
    public void forEach(BiConsumer<? super List<K>, ? super V> consumer) {
        throw new UnsupportedOperationException();
    }

    public void forEach1(BiConsumer<? super List<T>, ? super V> consumer) {
        recursionEntry(root, consumer, new SimpleList<>());
    }

    private static <K, V> void recursionEntry(REntry<K, V> parent, BiConsumer<? super List<K>, ? super V> consumer, SimpleList<K> list) {
        for (REntry<K, V> entry : parent) {
            list.add(entry.k);
            if (entry.v != NOT_USING) {
                consumer.accept(list, entry.v);
            }
            recursionEntry(entry, consumer, list);
            list.size--;
        }
    }

    private static class MapItr<T, V> extends AbstractIterator<REntry<T, V>> {
        SimpleList<REntry<T, V>> a = new SimpleList<>(), b = new SimpleList<>();
        int listIndex = 0;

        public MapItr(REntry<T, V> root) {
            a.add(root);
        }

        @Override
        public boolean computeNext() {
            final SimpleList<REntry<T, V>> a = this.a;
            if(a.size == 0)
                return false;

            while (listIndex < a.size) {
                if((result = a.get(listIndex++)).v != NOT_USING)
                    return true;
            }
            listIndex = 0;

            final SimpleList<REntry<T, V>> b = this.b;
            for (REntry<T, V> entry : a) {
                if(entry.size > 0) {
                    b.ensureCapacity(b.size + entry.size);
                    for (REntry<T, V> subEntry : entry) {
                        b.add(subEntry);
                    }
                }
            }

            // Swap
            SimpleList<REntry<T, V>> tmp1 = this.a;
            tmp1.size = 0;
            this.a = this.b;
            this.b = tmp1;

            return computeNext();
        }

        /*@Override
        @Deprecated
        public void remove(REntry<K, V> currentObject) {
            currentObject.v = (V) NOT_USING;
            // unable to find father, so, size would be wrong....
        }*/
    }

    static final class Values<V> extends AbstractCollection<V> {
        private final UnsortedMultiKeyMap<?, ?, V> map;

        public Values(UnsortedMultiKeyMap<?, ?, V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<V> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new Iterator<V>() {
                final MapItr<?, V> itr = new MapItr<>(map.root);

                @Override
                public boolean hasNext() {
                    return itr.hasNext();
                }

                @Override
                public V next() {
                    return itr.next().v;
                }
            };
        }

        public final boolean contains(Object o) {
            return map.containsValue(o);
        }

        public final boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }
}