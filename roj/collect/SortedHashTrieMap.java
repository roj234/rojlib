package roj.collect;

import roj.concurrent.OperationDone;
import roj.math.MutableBoolean;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiConsumer;

import static roj.collect.IntMap.NOT_USING;

/**
 * 泛用常数时间合成决定系统1.4
 * Generic Constant Time Crafting Determination System Version 1.4
 */
public final class SortedHashTrieMap<K, V> implements Map<List<K>, V> {
    public interface SortComparator<T> extends Comparator<T> {
        boolean equalsTo(T o1, Object o2, int hashId);
        T toImmutable(T t);

        int maxHashLength();
        default int hashLength(T t) {
            return maxHashLength();
        }
        int hashAt(T t, int hashId);
    }

    public static final class REntry<K, V> implements Iterable<REntry<K, V>> {
        K k;
        V v;
        byte hashId;

        REntry<K, V> next;

        REntry<?, ?>[] children;
        int size = 0;

        static final int INITIAL_LENGTH = 1;
        static final float LOAD_FACTOR = 0.95f;

        @SuppressWarnings("unchecked")
        REntry(K k) {
            this.k = k;
            this.v = (V) NOT_USING;
        }

        REntry(REntry<K, V> entry) {
            if(entry.children != null)
                this.children = new REntry<?, ?>[entry.children.length];
            this.size = entry.size;
            this.hashId = entry.hashId;
            this.k = entry.k;
            this.v = entry.v;
        }

        @SuppressWarnings("unchecked")
        void resize(SortComparator<K> c) {
            final REntry<?, ?>[] children = this.children;
            REntry<?, ?>[] newEntries = new REntry<?, ?>[children.length << 1];
            REntry<K, V> entry, next;

            int flg = newEntries.length - 1;

            int i = 0, j = children.length;
            for (; i < j; i++) {
                entry = (REntry<K, V>) children[i];
                while (entry != null) {
                    next = entry.next;

                    int newKey = c.hashAt(entry.k, entry.hashId & 0xFF) & flg;

                    REntry<K, V> old = (REntry<K, V>) newEntries[newKey];

                    newEntries[newKey] = entry;
                    entry.next = old;
                    entry = next;
                }
            }

            this.children = newEntries;
        }

        @SuppressWarnings("unchecked")
        public void gatherChildrenMulti(SortComparator<K> c, K key, List<REntry<K, V>> destination) {
            final REntry<?, ?>[] children = this.children;
            if(children == null)
                return;
            final int flg = children.length - 1;

            for (int i = 0; i < c.hashLength(key); i++) {
                int hash = c.hashAt(key, i);

                REntry<K, V> entry = (REntry<K, V>) children[hash & flg];
                while (entry != null) {
                    if (c.equalsTo(key, entry.k, i)) {
                        destination.add(entry);
                    }
                    entry = entry.next;
                }
            }
        }

        @SuppressWarnings("unchecked")
        public REntry<K, V> getChild(SortComparator<K> c, K key) {
            final REntry<?, ?>[] children = this.children;
            if(children == null)
                return null;
            final int flg = children.length - 1;

            for (int i = 0; i < c.hashLength(key); i++) {
                int hash = c.hashAt(key, i);

                REntry<K, V> entry = (REntry<K, V>) children[hash & flg];
                while (entry != null) {
                    if (c.equalsTo(key, entry.k, i)) {
                        return entry;
                    }
                    entry = entry.next;
                }
            }

            return null;
        }

        public boolean putChild(SortComparator<K> c, REntry<K, V> entryIn) {
            return putChild(c, entryIn, 0);
        }

        @SuppressWarnings("unchecked")
        public boolean putChild(SortComparator<K> c, REntry<K, V> entryIn, int hashId) {
            REntry<?, ?>[] children = this.children;
            if (children == null) {
                this.children = children = new REntry<?, ?>[INITIAL_LENGTH];
            } else if (size > ((children.length == INITIAL_LENGTH && INITIAL_LENGTH < 8) ? (INITIAL_LENGTH + 1) : children.length * LOAD_FACTOR)) {
                resize(c);
                children = this.children;
            }

            entryIn.hashId = (byte) hashId;

            REntry<K, V> entry;

            if(hashId > c.maxHashLength())
                throw new IllegalArgumentException("Too many hashes!");

            hashId = c.hashAt(entryIn.k, hashId) & (children.length - 1);

            if ((entry = (REntry<K, V>) children[hashId]) == null) {
                children[hashId] = entryIn;
            } else {
                final K k = entryIn.k;
                while (true) {
                    if (c.equalsTo(k, entry.k, hashId)) // has
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
        public boolean removeChild(SortComparator<K> c, K key) {
            final REntry<?, ?>[] children = this.children;
            if (children == null) {
                return false;
            }
            int flg = children.length - 1;

            int hashId = -1;
            REntry<K, V> entry, prev = null, toRemove = null;
            outer:
            for (int i = 0; i < c.hashLength(key); i++) {
                hashId = c.hashAt(key, i) & flg;

                entry = (REntry<K, V>) children[hashId];
                while (entry != null) {
                    if (c.equalsTo(key, entry.k, i)) {
                        toRemove = entry;
                        break outer;
                    }
                    prev = entry;
                    entry = entry.next;
                }
            }

            if(toRemove == null) {
                System.err.println("all is " + c.maxHashLength() + " and not at " + hashId);
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
            return "E{" + k + "=" + v /*+ (size > 0 ? ",child=" + new MyHashSet<>(this).toString() : "")*/ + "}";
        }

        public void clear() {
            if (size == 0)
                return;
            size = 0;
            children = null;
        }

        int recursionSum() {
            int i = v != NOT_USING ? 1 : 0;
            if (size > 0) {
                for (REntry<K, V> value : this) {
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
        public Iterator<REntry<K, V>> iterator() {
            if(children == null)
                return Collections.emptyIterator();
            return new AbstractIterator<REntry<K, V>>() {
                REntry<K, V> entry;
                int i = 0;

                @Override
                public boolean computeNext() {
                    while (true) {
                        if (entry != null) {
                            result = entry;
                            entry = entry.next;
                            return true;
                        } else if (i < children.length) {
                            this.entry = (REntry<K, V>) children[i++];
                        } else {
                            return false;
                        }
                    }
                }
            };
        }

        public int copyFrom(SortComparator<K> c, REntry<K, V> node) {
            int v = 0;
            if(node.v != IntMap.NOT_USING && this.v == IntMap.NOT_USING) {
                this.v = node.v;
                v = 1;
            }

            for (REntry<K, V> entry : node) {
                REntry<K, V> sub = getChild(c, entry.k);
                if (sub == null) putChild(c, sub = new REntry<>(this));
                v += sub.copyFrom(c, entry);
            }
            return v;
        }
    }
/*
    public static void main(String[] args) throws InterruptedException {
        int count = Integer.parseInt(args[0]);
        System.out.println("数据量：" + count);

        long t = System.currentTimeMillis();

        SortComparator<String> comparatorSp = new SortComparator<String>() {
            @Override
            public boolean equalsTo(String o1, Object o2, int hashId) {
                return o1.equals(o2);
            }

            @Override
            public String toImmutable(String s) {
                return s;
            }

            @Override
            public int maxHashLength() {
                return 1;
            }

            @Override
            public int hashAt(String s, int hashId) { // 3, 6, 9
                *//*hashId = Math.min((hashId + 1) * 3, s.length());

                int h = 0;
                for (int i = 0; i < hashId; i++) {
                    h = h * 31 + s.charAt(i);
                }*//*
                final int hashCode = s.hashCode();
                return (hashCode >>> 16) | hashCode;
            }

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };
        SortedHashTrieMap<String, Boolean> testingMap = new SortedHashTrieMap<>(comparatorSp, 10);

        Map<List<String>, Boolean> map = new HashMap<>();

        List<List<String>> toFindShuffled = new ArrayList<>(count);
        List<List<String>> toFind = new ArrayList<>(count);

        Random r = new Random(10);

        MyHashSet<String> ss = new MyHashSet<>();
        final int i1 = 10000;
        String[] keys = new String[i1];
        CharList cl = new CharList();
        int dup = 0;
        for (int i = 0; i < i1; i++) {
            cl.clear();
            int m = r.nextInt(5) + 1;
            for (int k = 0; k < m; k++) {
                cl.append((char)r.nextInt(65536));
            }
            final String s = cl.toString();
            if(!ss.add(s)) {
                dup ++;
            }
            s.hashCode();
            keys[i] = ss.find(s);
        }
        ss.slowClear();
        System.out.println("重复key: " + dup);

        // writeTest
        for (int i = 0; i < count; i++) {
            int l = r.nextInt(10) + 1;

            final String[] e = new String[l];
            final String[] e2 = new String[l];
            for (int j = 0; j < l; j++) {
                e[j] = e2[j] = keys[r.nextInt(i1)];
            }

            ArrayUtil.shuffle(e2, r);
            toFindShuffled.add(Arrays.asList(e2));
            toFind.add(Arrays.asList(e));
        }

        System.out.println("准备时间: " + (System.currentTimeMillis() - t));
        t = System.currentTimeMillis();

        for (List<String> tmp : toFind) {
            map.put(tmp, true);
        }

        System.out.println("Map Put: " + (System.currentTimeMillis() - t));
        t = System.currentTimeMillis();


        for (List<String> tmp : toFind) {
            testingMap.put(tmp, true);
        }

        System.out.println("SHTM Put: " + (System.currentTimeMillis() - t));
        final MyHashSet<REntry<String, Boolean>> rEntries = new MyHashSet<>();
        Iterator<REntry<String, Boolean>> itr = testingMap.mapItr();
        while (itr.hasNext()) {
            rEntries.add(itr.next());
        }

        final MutableInt mi = new MutableInt();
        testingMap.forEach((k, v) -> {
            mi.increment();
        });

        System.out.println("R.LEN: " + map.size());
        System.out.println("M.LEN: " + rEntries.size);
        System.out.println("I.LEN: " + mi.getValue());
        System.out.println("T.LEN: " + testingMap.size);
        t = System.currentTimeMillis();

        for (List<String> tmp : toFindShuffled) {
            if(testingMap.get(tmp) != Boolean.TRUE) {
                System.out.println("ERROR! " + tmp);
            }
        }

        System.out.println("SHTM Get: " + (System.currentTimeMillis() - t));
        t = System.currentTimeMillis();

        for (List<String> tmp : toFind) {
            outer:
            for (Map.Entry<List<String>, Boolean> entry : map.entrySet()) {
                List<String> list = entry.getKey();
                if(list.size() == tmp.size()) {
                    for (int i = 0; i < list.size(); i++) {
                        if(!list.get(i).equals(tmp.get(i))) {
                            continue outer;
                        }
                    }
                }
                Boolean value = entry.getValue();
            }
        }

        System.out.println("Map Get: " + (System.currentTimeMillis() - t));

        Thread.sleep(100000);

        System.out.println(testingMap);
    }*/

    final SortComparator<K> comparator;
    final K[] kBuf;

    REntry<K, V> root = new REntry<>((K)null);
    int size = 0;

    public SortedHashTrieMap(SortComparator<K> comparator) {
        this(comparator, 4);
    }

    public SortedHashTrieMap(SortComparator<K> comparator, int len) {
        this.comparator = comparator;
        this.kBuf = Helpers.cast(new Object[len]);
    }

    public SortedHashTrieMap(SortComparator<K> comparator, int len, Map<List<K>, V> map) {
        this(comparator, len);
        putAll(map);
    }

    REntry<K, V> entryForPut(List<K> s, int i, int len, int hashId) {
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        K[] ks = sort(s, i, len);

        REntry<K, V> entry = root;
        REntry<K, V> prev;
        for (i = 0; i < len; i++) {
            K k = ks[i];
            prev = entry;
            entry = entry.getChild(comparator, k);
            if (entry == null) {
                prev.putChild(comparator, entry = new REntry<>(comparator.toImmutable(k)), hashId);
            }
        }
        if (entry.v == NOT_USING) {
            size++;
        }
        return entry;
    }

    public REntry<K, V> getEntry(List<K> s, int i, int len) {
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        K[] ks = sort(s, i, len);

        REntry<K, V> entry = root;
        for (i = 0; i < len; i++) {
            entry = entry.getChild(comparator, ks[i]);
            if (entry == null) {
                if(ks == kBuf)
                    Arrays.fill(ks, null);
                return null;
            }
        }

        if(ks == kBuf)
            Arrays.fill(ks, null);
        return entry.v != NOT_USING ? entry : null;
    }

    public REntry<K, V> getRoot() {
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
    public V put(List<K> key, V e) {
        return put(key, 0, key.size(), e, 0);
    }

    public V put(List<K> key, V e, int hashId) {
        return put(key, 0, key.size(), e, hashId);
    }

    public V put(List<K> key, int len, V e, int hashId) {
        return put(key, 0, len, e, hashId);
    }

    public V put(List<K> key, int off, int len, V e, int hashId) {
        REntry<K, V> entry = entryForPut(key, off, len, hashId);
        V v = entry.v;
        entry.v = e;
        return v;
    }

    public void addTree(SortedHashTrieMap<? extends List<K>, ? extends V> m) {
        size += root.copyFrom(comparator, Helpers.cast(m.root));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends List<K>, ? extends V> m) {
        if (m instanceof SortedHashTrieMap) {
            addTree((SortedHashTrieMap<? extends List<K>, ? extends V>) m);
            return;
        }
        for (Map.Entry<? extends List<K>, ? extends V> entry : m.entrySet()) {
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
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        SimpleList<REntry<K, V>> list = new SimpleList<>(Math.min(len, kBuf.length));

        K[] ks = sort(s, i, len);

        REntry<K, V> entry = root;
        for (i = 0; i < len; i++) {
            entry = entry.getChild(comparator, ks[i]);
            if (entry == null)
                return null;

            list.add(entry);
        }

        if(ks == kBuf)
            Arrays.fill(ks, null);

        if (entry.v == NOT_USING)
            return null;

        size--;

        if (!list.isEmpty()) {
            i = list.size;

            while (--i >= 0) {
                REntry<K, V> prev = list.get(i);

                if (prev.recursionSum() > 0) {
                    prev.removeChild(comparator, entry.k);
                    break;
                }
                entry = prev;
            }
            list.list = null;
        }

        return entry.v;
    }

    private K[] sort(List<K> s, int i, int len) {
        K[] ks = kBuf;
        if(len > ks.length) {
            ks = Helpers.cast(new Object[len]);
        }
        len += i;
        for (int j = i; j < len; j++) {
            ks[j - i] = s.get(j);
        }
        Arrays.sort(ks, 0, len - i, comparator);
        return ks;
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
        return getMulti(s, 0, s.size(), limit, new SimpleList<>());
    }

    public List<V> getMulti(List<K> s, int len, int limit) {
        return getMulti(s, 0, len, limit, new SimpleList<>());
    }

    public List<V> getMulti(List<K> s, int i, int len, int limit) {
        return getMulti(s, i, len, limit, new SimpleList<>());
    }

    public List<V> getMulti(List<K> s, int i, int len, int limit, List<V> dest) {
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        K[] ks = sort(s, i, len);

        dest.clear();
        SimpleList<REntry<K, V>> tmp1 = Helpers.cast(dest);
        tmp1.add(root);

        SimpleList<REntry<K, V>> tmp2 = new SimpleList<>(ks.length);

        SimpleList<REntry<K, V>> tmp3;
        for (i = 0; i < len; i++) {
            for (REntry<K, V> entry : tmp1) {
                entry.gatherChildrenMulti(comparator, ks[i], tmp2);
            }

            // Swap
            tmp3 = tmp2;
            tmp2 = tmp1;
            tmp2.size = 0;
            tmp1 = tmp3;

            if (tmp1.isEmpty()) {
                if(ks == kBuf)
                    Arrays.fill(ks, null);
                return Collections.emptyList();
            }
        }

        if(ks == kBuf)
            Arrays.fill(ks, null);

        for (int j = 0; j < tmp1.size(); j++) {
            dest.set(j, tmp1.get(j).v);
        }

        return dest;
    }

    public Map.Entry<Integer, V> longestMatches(List<K> s) {
        return longestMatches(s, 0, s.size());
    }

    public Map.Entry<Integer, V> longestMatches(List<K> s, int len) {
        return longestMatches(s, 0, len);
    }

    public Map.Entry<Integer, V> longestMatches(List<K> s, int i, int len) {
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        int length = 0;

        K[] ks = sort(s, i, len);

        REntry<K, V> entry = root;
        for (i = 0; i < len; i++) {
            REntry<K, V> next = entry.getChild(comparator, ks[i]);
            if (next == null)
                break;
            length++;
            entry = next;
        }

        if(ks == kBuf)
            Arrays.fill(ks, null);

        return new AbstractMap.SimpleImmutableEntry<>(length, entry.v);
    }

    public List<V> valueMatches(List<K> s, int limit) {
        return valueMatches(s, 0, s.size(), limit);
    }

    public List<V> valueMatches(List<K> s, int len, int limit) {
        return valueMatches(s, 0, len, limit);
    }

    public List<V> valueMatches(List<K> s, int i, int len, int limit) {
        SimpleList<K> base = new SimpleList<>(len - i);
        REntry<K, V> entry = matches(s, i, len, base);
        if (entry == null)
            return Collections.emptyList();

        ArrayList<V> values = new ArrayList<>(Math.min(entry.recursionSum(), limit));
        try {
            recursionEntry(entry, (k, v) -> {
                if (values.size() >= limit)
                    throw OperationDone.INSTANCE;
                values.add(v);
            }, base);
        } catch (OperationDone ignored) {}
        return values;
    }

    @Deprecated
    public List<Map.Entry<K[], V>> entryMatches(List<K> s, int limit) {
        return entryMatches(s, 0, s.size(), limit);
    }

    @Deprecated
    public List<Map.Entry<K[], V>> entryMatches(List<K> s, int len, int limit) {
        return entryMatches(s, 0, len, limit);
    }

    @Deprecated
    public List<Map.Entry<K[], V>> entryMatches(List<K> s, int i, int len, int limit) {
        SimpleList<K> base = new SimpleList<>(len - i);
        REntry<K, V> entry = matches(s, i, len, base);
        if (entry == null)
            return Collections.emptyList();

        ArrayList<Map.Entry<Object[], V>> entries = new ArrayList<>(Math.min(entry.recursionSum(), limit));
        try {
            recursionEntry(entry, (k, v) -> {
                if (entries.size() >= limit)
                    throw OperationDone.INSTANCE;
                entries.add(new AbstractMap.SimpleImmutableEntry<>(k.toArray(), v));
            }, base);
        } catch (OperationDone ignored) {}
        return Helpers.cast(entries);
    }

    private REntry<K, V> matches(List<K> s, int i, int len, List<K> base) {
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        K[] ks = sort(s, i, len);

        REntry<K, V> entry = root;
        for (i = 0; i < len; i++) {
            entry = entry.getChild(comparator, ks[i]);
            if (entry == null) {
                if(ks == kBuf)
                    Arrays.fill(ks, null);

                return null;
            }
            base.add(entry.k);
        }

        if(ks == kBuf)
            Arrays.fill(ks, null);
        return entry;
    }

    /**
     * internal: nodes.forEach(if(s.startsWith(node)))
     * s: abcdef
     * node: abcdef / abcde / abcd... true
     * node: abcdefg  ... false
     */
    public boolean startsWith(List<K> s) {
        return startsWith(s, 0, s.size());
    }

    public boolean startsWith(List<K> s, int len) {
        return startsWith(s, 0, len);
    }

    public boolean startsWith(List<K> s, int i, int len) {
        len = len - i;
        if(len < 0)
            throw new IllegalArgumentException("delta length < 0");

        REntry<K, V> entry = root;

        K[] ks = sort(s, i, len);

        for (i = 0; i < len; i++) {
            entry = entry.getChild(comparator, ks[i]);
            if (entry == null) {
                if(ks == kBuf)
                    Arrays.fill(ks, null);

                return false;
            }

            if (entry.v != NOT_USING) {
                break;
            }
        }

        if(ks == kBuf)
            Arrays.fill(ks, null);
        return entry.v != NOT_USING;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object id) {
        List<K> s = (List<K>) id;
        return get(s, 0, s.size());
    }

    public V get(List<K> s, int length) {
        return get(s, 0, length);
    }

    public V get(List<K> s, int offset, int length) {
        REntry<K, V> entry = getEntry(s, offset, length);
        return entry == null ? null : entry.v;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TrieTree").append('{');
        forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
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
        Set<List<K>> set = new MyHashSet<>(size);
        forEach((k, v) -> set.add(k));
        return Collections.unmodifiableSet(set);
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
    public Set<Map.Entry<List<K>, V>> entrySet() {
        Set<Map.Entry<List<K>, V>> set = new MyHashSet<>(size);
        forEach((k, v) -> set.add(new AbstractMap.SimpleImmutableEntry<>(k, v)));
        return Collections.unmodifiableSet(set);
    }

    public Iterator<REntry<K, V>> mapItr() {
        return new MapItr<>(root);
    }

    @Override
    public void forEach(BiConsumer<? super List<K>, ? super V> consumer) {
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

    private static class MapItr<K, V> extends AbstractIterator<REntry<K, V>> {
        SimpleList<REntry<K, V>> a = new SimpleList<>(), b = new SimpleList<>();
        int listIndex = 0;

        public MapItr(REntry<K, V> root) {
            a.add(root);
        }

        @Override
        public boolean computeNext() {
            final SimpleList<REntry<K, V>> a = this.a;
            if(a.size == 0)
                return false;

            while (listIndex < a.size) {
                if((result = a.get(listIndex++)).v != NOT_USING)
                    return true;
            }
            listIndex = 0;

            final SimpleList<REntry<K, V>> b = this.b;
            for (REntry<K, V> entry : a) {
                if(entry.size > 0) {
                    b.ensureCapacity(b.size + entry.size);
                    for (REntry<K, V> subEntry : entry) {
                        b.add(subEntry);
                    }
                }
            }

            // Swap
            SimpleList<REntry<K, V>> tmp1 = this.a;
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
        private final SortedHashTrieMap<?, V> map;

        public Values(SortedHashTrieMap<?, V> map) {
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