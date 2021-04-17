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
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.collect.IntMap.NOT_USING;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/2/21 10:39
 */
public class TrieTree<V> implements Map<CharSequence, V> {
    static final int COMPRESS_START_DEPTH = 1,
            MIN_REMOVE_ARRAY_SIZE = 8;

    static class Entry<V> extends TrieEntry {
        V value;

        @SuppressWarnings("unchecked")
        Entry(char c) {
            super(c);
            this.value = (V) NOT_USING;
        }

        Entry(char c, Entry<V> entry) {
            super(c);
            this.value = entry.value;
        }
        
        int recursionSum() {
            int i = value == IntMap.NOT_USING ? 0 : 1;
            if (size > 0) {
                for (TrieEntry value : this) {
                    i += value.recursionSum();
                }
            }
            return i;
        }

        @SuppressWarnings("unchecked")
        public int copyFrom(TrieEntry x) {
            Entry<?> node = (Entry<?>) x;
            int v = 0;
            if(node.value != IntMap.NOT_USING && value == IntMap.NOT_USING) {
                this.value = (V) node.value;
                v = 1;
            }

            for (TrieEntry entry : node) {
                TrieEntry sub = getChild(entry.c);
                if (sub == null) putChild(sub = newInstance());
                v += sub.copyFrom(entry);
            }
            return v;
        }

        @Override
        protected Entry<V> newInstance() {
            Entry<V> entry = new Entry<>(c);
            entry.value = value;
            return entry;
        }

        public V getValue() {
            if (value == IntMap.NOT_USING)
                throw new UnsupportedOperationException();
            return value;
        }

        public V setValue(V value) {
            if (value == IntMap.NOT_USING)
                throw new UnsupportedOperationException();
            V ov = this.value;
            this.value = value;
            return ov;
        }
    }

    static final class PEntry<V> extends Entry<V> {
        CharSequence val;

        PEntry(CharSequence val) {
            super(val.charAt(0));
            this.val = val;
        }

        PEntry(CharSequence val, Entry<V> entry) {
            super(val.charAt(0), entry);
            this.val = val;
        }

        @Override
        protected Entry<V> newInstance() {
            Entry<V> entry = new PEntry<>(val);
            entry.value = value;
            return entry;
        }

        CharSequence text() {
            return val;
        }

        @Override
        void append(CharList sb) {
            sb.append(val);
        }

        @Override
        int length() {
            return val.length();
        }

        @Override
        public String toString() {
            return "PE{" + val + '}';
        }
    }

    Entry<V> root = new Entry<>((char) 0);
    int size = 0;

    public TrieTree() {
    }

    public TrieTree(Map<CharSequence, V> map) {
        putAll(map);
    }

    public void addTrieTree(TrieTree<? extends V> m) {
        size += root.copyFrom(Helpers.cast(m.root));
    }

    @SuppressWarnings("unchecked")
    Entry<V> entryForPut(CharSequence s, int i, int len) {
        if (len - i < 0)
            throw new IllegalArgumentException("delta length < 0");

        Entry<V> entry = root;
        Entry<V> prev;
        for (; i < len; i++) {
            char c = s.charAt(i);
            prev = entry;
            entry = (Entry<V>) entry.getChild(c);
            if (entry == null) {
                // 前COMPRESS_START_DEPTH个字符, 避免频繁插入带来效率损失
                if (len - i == 1 || i < COMPRESS_START_DEPTH) {
                    prev.putChild(entry = new Entry<>(c));
                } else {
                    prev.putChild(entry = new PEntry<>(s.subSequence(i, len)));
                    break;
                }
            } else if (entry.text() != null) { // # split
                // PEntry

                final CharSequence text = entry.text();

                // lastMatch = 1; // of i(minAdd) off
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                // a - bcd
                if (lastMatch == text.length()) {
                    // do nothing... as it for next
                    i += lastMatch - 1;
                } else {

                    // [0 - 2) => bc
                    if (lastMatch == 1) {
                        prev.putChild(entry = new Entry<>(text.charAt(0), entry));
                    } else {
                        ((PEntry<V>) entry).val = text.subSequence(0, lastMatch);
                    }

                    // [2, 3) => d
                    Entry<V> child;
                    if (text.length() - 1 == lastMatch) {
                        // only one char
                        child = new Entry<>(text.charAt(lastMatch), entry);
                    } else {
                        child = new PEntry<>(text.subSequence(lastMatch, text.length()), entry);
                    }

                    entry.resetMap();
                    entry.value = (V) IntMap.NOT_USING;
                    entry.putChild(child);

                    //System.out.println("E to " + entry + " and " + child);

                    if (len - i - 1 == lastMatch) {
                        child = new Entry<>(s.charAt(len - 1));
                    } else {
                        if (len == i + lastMatch) {
                            // entry = child
                            break;
                        } else {
                            child = new PEntry<>(s.subSequence(i + lastMatch, len));
                        }
                    }

                    entry.putChild(child);
                    entry = child;

                    break;
                }
            }
        }
        if (entry.value == IntMap.NOT_USING) {
            size++;
            entry.value = null;
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    public Entry<V> getEntry(CharSequence s, int i, int len) {
        if (len - i < 0)
            throw new IllegalArgumentException("delta length < 0");

        Entry<V> entry = root;
        for (; i < len; i++) {
            entry = (Entry<V>) entry.getChild(s.charAt(i));
            if (entry == null)
                return null;
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length())
                    return null;
                i += text.length() - 1;
            }
        }
        return entry.value != IntMap.NOT_USING ? entry : null;
    }

    public Entry<V> getRoot() {
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

    @Override
    public V put(CharSequence key, V e) {
        return put(key, 0, key.length(), e);
    }

    public V put(CharSequence key, int len, V e) {
        return put(key, 0, len, e);
    }

    public V put(CharSequence key, int off, int len, V e) {
        Entry<V> entry = entryForPut(key, off, len);
        V v = entry.value;
        entry.value = e;

        return v;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends CharSequence, ? extends V> m) {
        if (m instanceof TrieTree) {
            addTrieTree((TrieTree<? extends V>) m);
            return;
        }
        for (Map.Entry<? extends CharSequence, ? extends V> entry : m.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object k) {
        CharSequence s = (CharSequence) k;
        return remove(s, 0, s.length(), NOT_USING);
    }

    public V remove(CharSequence s, int len) {
        return remove(s, 0, len, NOT_USING);
    }

    public V remove(CharSequence s, int i, int len) {
        return remove(s, i, len, NOT_USING);
    }

    @SuppressWarnings("unchecked")
    V remove(CharSequence s, int i, int len, Object tc) {
        if(len - i < 0)
            throw new IllegalArgumentException("delta length < 0");

        SimpleList<Entry<V>> list = new SimpleList<>(Math.min(len - i, MIN_REMOVE_ARRAY_SIZE));

        Entry<V> entry = root;
        for (; i < len; i++) {
            entry = (Entry<V>) entry.getChild(s.charAt(i));
            if (entry == null)
                return null;

            list.add(entry);

            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length())
                    return null;
                i += text.length() - 1;
            }
        }
        if(entry.value == NOT_USING)
            return null;
        if(!Objects.equals(entry.value, tc) && tc != NOT_USING)
            return null;

        size--;

        if (!list.isEmpty()) {
            i = list.size;

            while (--i >= 0) {
                Entry<V> prev = list.get(i);

                if (prev.recursionSum() != 0) {
                    prev.removeChild(entry);
                    break;
                }
                entry = prev;
            }
            list.size = i + 1;

            // 下面还有东西
            if (i >= COMPRESS_START_DEPTH) {
                entry = list.get(i);

                CharList sb = new CharList().append(entry.text() == null ? entry.c : entry.text());

                while (entry.childrenCount() == 1) {
                    entry = (Entry<V>) entry.iterator().next();

                    sb.append(entry.text() == null ? entry.c : entry.text());
                }

                if (sb.length() > 0) {
                    (COMPRESS_START_DEPTH > 0 && i == 0 ? root : list.get(i - 1)).putChild(sb.length() == 1 ? new Entry<>(sb.charAt(0), entry) : new PEntry<>(sb.toString(), entry));
                }
            }

            list.clear();
        }

        return entry.value;
    }

    @Override
    public boolean containsKey(Object i) {
        final CharSequence s = (CharSequence) i;
        return containsKey(s, 0, s.length());
    }

    public boolean containsKey(CharSequence s, int len) {
        return containsKey(s, 0, len);
    }

    public boolean containsKey(CharSequence s, int off, int len) {
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

    public Map.Entry<Integer, V> longestMatches(CharSequence s) {
        return longestMatches(s, 0, s.length());
    }

    public Map.Entry<Integer, V> longestMatches(CharSequence s, int len) {
        return longestMatches(s, 0, len);
    }

    @SuppressWarnings("unchecked")
    public Map.Entry<Integer, V> longestMatches(CharSequence s, int i, int len) {
        int d = 0;

        Entry<V> entry = root, next;
        for (; i < len; i++) {
            next = (Entry<V>) entry.getChild(s.charAt(i));
            if (next == null)
                break;
            entry = next;
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length()) {
                    d += lastMatch;
                    break;
                }
                i += text.length() - 1;
                d += text.length();
            } else {
                d++;
            }
        }

        return entry.value == NOT_USING ? null : new AbstractMap.SimpleImmutableEntry<>(d, entry.value);
    }

    public List<V> valueMatches(CharSequence s, int limit) {
        return valueMatches(s, 0, s.length(), limit);
    }

    public List<V> valueMatches(CharSequence s, int len, int limit) {
        return valueMatches(s, 0, len, limit);
    }

    public List<V> valueMatches(CharSequence s, int i, int len, int limit) {
        CharList base = new CharList();
        Entry<V> entry = matches(s, i, len, base);
        if (entry == null)
            return Collections.emptyList();

        ArrayList<V> values = new ArrayList<>(Math.min(entry.recursionSum(), limit));
        try {
            recursionEntry(entry, (k, v) -> {
                if (values.size() >= limit) {
                    throw OperationDone.INSTANCE;
                }
                values.add(v);
            }, base);
        } catch (OperationDone ignored) {}
        return values;
    }

    public List<Map.Entry<String, V>> entryMatches(CharSequence s, int limit) {
        return entryMatches(s, 0, s.length(), limit);
    }

    public List<Map.Entry<String, V>> entryMatches(CharSequence s, int len, int limit) {
        return entryMatches(s, 0, len, limit);
    }

    public List<Map.Entry<String, V>> entryMatches(CharSequence s, int i, int len, int limit) {
        CharList base = new CharList();
        Entry<V> entry = matches(s, i, len, base);
        if (entry == null)
            return Collections.emptyList();

        ArrayList<Map.Entry<String, V>> entries = new ArrayList<>(Math.min(entry.recursionSum(), limit));
        try {
            recursionEntry(entry, (k, v) -> {
                if (entries.size() >= limit) {
                    throw OperationDone.INSTANCE;
                }
                entries.add(new AbstractMap.SimpleImmutableEntry<>(k.toString(), v));
            }, base);
        } catch (OperationDone ignored) {}
        return entries;
    }

    @SuppressWarnings("unchecked")
    private Entry<V> matches(CharSequence s, int i, int len, CharList sb) {
        Entry<V> entry = root;
        for (; i < len; i++) {
            entry = (Entry<V>) entry.getChild(s.charAt(i));
            if (entry == null) {
                return null;
            }
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length()) {
                    if(lastMatch < len - i) // 字符串没匹配上
                        return null;

                    sb.append(text);
                    break;
                }
                i += text.length() - 1;
                sb.append(text);
            } else {
                sb.append(entry.c);
            }
        }

        return entry;
    }

    /**
     * internal: nodes.forEach(if(s.startsWith(node)))
     * s: abcdef
     * node: abcdef / abcde / abcd... true
     * node: abcdefg  ... false
     */
    public boolean startsWith(CharSequence s) {
        return startsWith(s, 0, s.length());
    }

    public boolean startsWith(CharSequence s, int len) {
        return startsWith(s, 0, len);
    }

    @SuppressWarnings("unchecked")
    public boolean startsWith(CharSequence s, int i, int len) {
        Entry<V> entry = root;
        for (; i < len; i++) {
            entry = (Entry<V>) entry.getChild(s.charAt(i));
            if (entry == null)
                return false;
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length()) {
                    return false;
                    // 不符合规定: entry.value != NOT_USING && lastMatch == len - i;
                }
                i += text.length() - 1;
            }

            if (entry.value != IntMap.NOT_USING)
                return true;
        }
        return entry.value != IntMap.NOT_USING;
    }

    @Override
    public V get(Object id) {
        CharSequence s = (CharSequence) id;
        return get(s, 0, s.length());
    }

    public V get(CharSequence s, int length) {
        return get(s, 0, length);
    }

    public V get(CharSequence s, int offset, int length) {
        Entry<V> entry = getEntry(s, offset, length);
        return entry == null ? null : entry.value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TrieTree").append('{');
        if (!isEmpty()) {
            forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
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
    public Set<CharSequence> keySet() {
        Set<CharSequence> set = new MyHashSet<>(size);
        forEach((k, v) -> set.add(k));
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    @Override
    public Collection<V> values() {
        return new Values<>(this);
    }

    public Iterator<Entry<V>> mapItr() {
        return new MapItr<>(root);
    }
    
    private static class MapItr<V> extends AbstractIterator<Entry<V>> {
        SimpleList<Entry<V>> a = new SimpleList<>(), b = new SimpleList<>();
        int listIndex = 0;

        public MapItr(Entry<V> root) {
            a.add(root);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean computeNext() {
            final SimpleList<Entry<V>> a = this.a;
            if(a.size == 0)
                return false;

            while (listIndex < a.size) {
                if((result = a.get(listIndex++)).value != NOT_USING)
                    return true;
            }
            listIndex = 0;

            final SimpleList<Entry<V>> b = this.b;
            for (Entry<V> entry : a) {
                if(entry.size > 0) {
                    b.ensureCapacity(b.size + entry.size);
                    for (TrieEntry subEntry : entry) {
                        b.add((Entry<V>) subEntry);
                    }
                }
            }

            // Swap
            SimpleList<Entry<V>> tmp1 = this.a;
            tmp1.size = 0;
            this.a = this.b;
            this.b = tmp1;

            return computeNext();

        }
    }

    static final class Values<V> extends AbstractCollection<V> {
        private final TrieTree<V> map;

        public Values(TrieTree<V> map) {
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
                final MapItr<V> itr = new MapItr<>(map.root);

                @Override
                public boolean hasNext() {
                    return itr.hasNext();
                }

                @Override
                public V next() {
                    return itr.next().value;
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

    /**
     * @see #forEach(BiConsumer)
     */
    @Nonnull
    @Override
    @Deprecated
    public Set<Map.Entry<CharSequence, V>> entrySet() {
        Set<Map.Entry<CharSequence, V>> set = new MyHashSet<>(size);
        forEach((k, v) -> set.add(new AbstractMap.SimpleImmutableEntry<>(k, v)));
        return Collections.unmodifiableSet(set);
    }

    @Override
    public void forEach(BiConsumer<? super CharSequence, ? super V> consumer) {
        recursionEntry(root, consumer, new CharList());
    }

    @SuppressWarnings("unchecked")
    private static <V> void recursionEntry(Entry<V> parent, BiConsumer<? super CharSequence, ? super V> consumer, CharList sb) {
        if (parent.value != IntMap.NOT_USING) {
            consumer.accept(sb.toString(), parent.value);
        }
        for (TrieEntry entry : parent) {
            entry.append(sb);
            recursionEntry((Entry<V>) entry, consumer, sb);
            sb.setIndex(sb.length() - entry.length());
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        int os = size;
        CharSequence cs = (CharSequence) key;
        remove(cs, 0, cs.length(), value);
        return os != size;
    }

    @Override
    public boolean replace(CharSequence key, V oldValue, V newValue) {
        Entry<V> entry = getEntry(key, 0, key.length());
        if(entry == null || entry.value == NOT_USING)
            return false;
        if(Objects.equals(oldValue, entry.value)) {
            entry.value = newValue;
            return true;
        }
        return false;
    }

    @Override
    public V compute(CharSequence key, BiFunction<? super CharSequence, ? super V, ? extends V> remappingFunction) {
        Entry<V> entry = getEntry(key, 0, key.length());
        V newV = remappingFunction.apply(key, entry == null || entry.value == NOT_USING ? null : entry.value);
        if(newV == null) {
            if(entry != null && entry.value != NOT_USING) {
                remove(key, 0, key.length());
            }
            return null;
        } else if(entry == null) {
            entry = entryForPut(key, 0, key.length());
        }

        if(entry.value == NOT_USING)
            size++;
        entry.value = newV;

        return newV;
    }

    @Override
    public V computeIfAbsent(CharSequence key, Function<? super CharSequence, ? extends V> mappingFunction) {
        Entry<V> entry = getEntry(key, 0, key.length());
        if(entry != null && entry.value != NOT_USING)
            return entry.value;
        if(entry == null) {
            entry = entryForPut(key, 0, key.length());
        }
        if(entry.value == NOT_USING)
            size++;
        return entry.value = mappingFunction.apply(key);
    }

    @Override
    public V computeIfPresent(CharSequence key,
                              BiFunction<? super CharSequence, ? super V, ? extends V> remappingFunction) {
        Entry<V> entry = getEntry(key, 0, key.length());
        if(entry == null || entry.value == NOT_USING)
            return null;
        if(entry.value == null)
            return null; // default implement guarantee
        V newV = remappingFunction.apply(key, entry.value);
        if(newV == null) {
            remove(key, 0, key.length());
            return null;
        }

        return entry.value = newV;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        CharSequence cs = (CharSequence) key;
        Entry<V> entry = getEntry(cs, 0, cs.length());
        if(entry == null || entry.value == NOT_USING)
            return defaultValue;
        return entry.value;
    }

    @Override
    public V putIfAbsent(CharSequence key, V value) {
        int os = size;
        Entry<V> entry = entryForPut(key, 0, key.length());
        if(os != size) {
            return entry.value = value;
        }
        return entry.value;
    }

    @Override
    public V replace(CharSequence key, V value) {
        Entry<V> entry = getEntry(key, 0, key.length());
        if(entry == null)
            return null;

        V v = entry.value;
        if(v == NOT_USING)
            v = null;

        entry.value = value;
        return v;
    }
}