package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.util.*;

import static roj.collect.IntMap.MAX_NOT_USING;

public class Int2IntMap implements CItrMap<Int2IntMap.Entry> {
    public int getOrDefault(int key, int def) {
        Entry entry = getEntry(key);
        return entry == null ? def : entry.v;
    }

    public Integer getOrDefault(int key, @Nullable Integer def) {
        Entry entry = getEntry(key);
        return entry == null ? def : Integer.valueOf(entry.v);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public static class Entry implements EntryIterable<Entry> {
        protected int k;
        public int v;

        protected Entry(int k, int v) {
            this.k = k;
            this.v = v;
        }

        public int getKey() {
            return k;
        }

        public int getValue() {
            return v;
        }

        public int setValue(int i) {
            int v = this.v;
            this.v = i;
            return v;
        }

        public void setKey(int k) {
            this.k = k;
        }

        protected Entry next;

        @Override
        public String toString() {
            return "Entry{" + k + "=" + v + '}';
        }

        @Override
        public Entry nextEntry() {
            return next;
        }
    }

    protected Entry[] entries;
    protected int size = 0;

    int length = 2;
    int mask = 1;
    float loadFactor = 0.8f;

    public Int2IntMap() {
        this(16);
    }

    public Int2IntMap(int size) {
        ensureCapacity(size);
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);
        mask = length - 1;

        if (this.entries != null)
            resize();
    }

    public Set<Entry> entrySet() {
        return new EntrySet(this);
    }

    public int size() {
        return size;
    }

    @Override
    public void removeEntry0(Entry entry) {
        remove(entry.k);
    }

    void afterPut(Entry key) {
    }

    protected void resize() {
        Entry[] newEntries = new Entry[length];
        Entry entry;
        Entry next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = entries[i];
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.k);
                Entry entry2 = newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = entry2;
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    public Integer put(int key, int e) {
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        int k2 = key - 1;

        Entry entry = getOrCreateEntry(key, k2);
        if (entry.k == k2) {
            entry.k = key;
            afterPut(entry);
            size++;
            entry.v = e;
            return null;
        }
        Integer oldV = entry.v;
        entry.v = e;
        return oldV;
    }

    public int putInt(int key, int e) {
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        int k2 = key - 1;

        Entry entry = getOrCreateEntry(key, k2);
        if (entry.k == k2) {
            entry.k = key;
            afterPut(entry);
            size++;
            entry.v = e;
            return -1;
        }
        int oldV = entry.v;
        entry.v = e;
        return oldV;
    }

    void afterRemove(Entry entry) {
    }

    public boolean remove(int id) {
        Entry prevEntry = null;
        Entry toRemove = null;
        {
            Entry entry = getEntryFirst(id, -1, false);
            while (entry != null) {
                if (entry.k == id) {
                    toRemove = entry;
                    break;
                }
                prevEntry = entry;
                entry = entry.next;
            }
        }

        if (toRemove == null)
            return false;

        afterRemove(toRemove);

        this.size--;

        if (prevEntry != null) {
            prevEntry.next = toRemove.next;
        } else {
            this.entries[indexFor(id)] = toRemove.next;
        }

        putRemovedEntry(toRemove);

        return true;
    }

    public boolean containsKey(int i) {
        return getEntry(i) != null;
    }

    public Entry getEntry(int id) {
        Entry entry = getEntryFirst(id, -1, false);
        while (entry != null) {
            if (entry.k == id)
                return entry;
            entry = entry.next;
        }
        return null;
    }

    public Entry getEntryOrCreate(int key, int def) {
        Entry entry = getOrCreateEntry(key, key - 1);
        if (entry.k == key - 1) {
            entry.k = key;
            entry.v = def;
            afterPut(entry);
            size++;
            return entry;
        }
        return entry;
    }

    public Entry getEntryOrCreate(int key) {
        return getEntryOrCreate(key, 0);
    }

    Entry getOrCreateEntry(int id, int def) {
        Entry entry = getEntryFirst(id, def, true);
        if (entry.k == def)
            return entry;
        while (true) {
            if (entry.k == id)
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;
        }
        Entry firstUnused = getCachedEntry(def, -1);
        entry.next = firstUnused;
        return firstUnused;
    }

    int indexFor(int id) {
        return (id ^ (id >>> 16)) & mask;
    }

    protected Entry notUsing = null;

    protected Entry getCachedEntry(int id, int val) {
        Entry cached = this.notUsing;
        if (cached != null) {
            cached.k = id;
            cached.v = val;
            this.notUsing = cached.next;
            cached.next = null;
            return cached;
        }

        return new Entry(id, val);
    }

    protected void putRemovedEntry(Entry entry) {
        if (notUsing != null && notUsing.k > MAX_NOT_USING) {
            return;
        }
        entry.next = notUsing;
        entry.k = notUsing == null ? 1 : notUsing.k + 1;
        notUsing = entry;
    }

    Entry getEntryFirst(int id, int def, boolean create) {
        id = indexFor(id);
        if (entries == null) {
            if (!create)
                return null;
            entries = new Entry[length];
        }
        Entry entry;
        if ((entry = entries[id]) == null) {
            if (!create)
                return null;
            entries[id] = entry = getCachedEntry(def, 0);
        }
        return entry;
    }

    public Integer get(int id) {
        Entry entry = getEntry(id);
        return entry == null ? null : entry.getValue();
    }

    Entry getValueEntry(int value) {
        if (entries == null)
            return null;
        for (int i = 0; i < length; i++) {
            Entry entry = entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                if (value == entry.v) {
                    return entry;
                }
                entry = entry.next;
            }
        }
        return null;
    }

    private boolean containsValue(int o) {
        return getValueEntry(o) != null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Int2IntMap").append('{');
        for (Entry entry : new EntrySet(this)) {
            sb.append(entry.k).append('=').append(entry.v).append(',');
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
        if (entries != null)
            entries = null;
        if(notUsing != null) {
            notUsing = null;
        }
    }

    public void clear() {
        if (size == 0)
            return;
        size = 0;
        if (entries != null)
            if (notUsing == null || notUsing.v < MAX_NOT_USING) {
                for (int i = 0; i < length; i++) {
                    if (entries[i] != null) {
                        putRemovedEntry(Helpers.cast(entries[i]));
                        entries[i] = null;
                    }
                }
            } else Arrays.fill(entries, null);
    }

    static class KeyItr extends MapItr<Entry> implements PrimitiveIterator.OfInt {
        KeyItr(Entry[] entries, CItrMap<Entry> map) {
            super(entries, map);
        }

        @Override
        public Integer next() {
            return nextT().k;
        }

        @Override
        public int nextInt() {
            return nextT().k;
        }
    }

    static class ValItr extends MapItr<Entry> implements PrimitiveIterator.OfInt {
        ValItr(Entry[] entries, CItrMap<Entry> map) {
            super(entries, map);
        }

        @Override
        public Integer next() {
            return nextT().v;
        }

        @Override
        public int nextInt() {
            return nextT().v;
        }
    }

    public static class KeySet extends AbstractSet<Integer> {
        private final Int2IntMap map;

        private KeySet(Int2IntMap map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final PrimitiveIterator.OfInt iterator() {
            return new KeyItr(map.entries, map);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry e = (Entry) o;
            int key = e.getKey();
            Entry comp = map.getEntry(key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (!(o instanceof Number)) {
                return false;
            }
            Entry entry = map.getEntry(((Number) o).intValue());
            return entry != null && map.remove(entry.k);
        }
    }

    public static class Values extends AbstractCollection<Integer> {
        private final Int2IntMap map;

        private Values(Int2IntMap map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final PrimitiveIterator.OfInt iterator() {
            return new ValItr(map.entries, map);
        }

        public final boolean contains(Object o) {
            return o instanceof Number && map.containsValue(((Number) o).intValue());
        }

        public final boolean remove(Object o) {
            if (!(o instanceof Number)) {
                return false;
            }
            Entry entry = map.getValueEntry(((Number) o).intValue());
            return entry != null && map.remove(entry.k);
        }

        public final Spliterator<Integer> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }

    static class EntrySet extends AbstractSet<Entry> {
        private final Int2IntMap map;

        private EntrySet(Int2IntMap map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<Entry> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new EntryItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry e = (Entry) o;
            int key = e.getKey();
            Entry comp = map.getEntry(key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Entry e = (Entry) o;
                return map.remove(e.k);
            }
            return false;
        }

        public final Spliterator<Entry> spliterator() {
            return Spliterators.spliterator(map.entries, 0);
        }
    }
}