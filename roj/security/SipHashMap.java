package roj.security;

import roj.collect.MyHashMap;

import java.util.Map;
import java.util.Objects;

import static roj.collect.IntMap.NOT_USING;

/**
 * @author solo6975
 * @since 2022/3/21 14:37
 */
public class SipHashMap<K extends CharSequence, V> extends MyHashMap<K, V> {
    private SipHash hash;
    private int mask;

    public SipHashMap() {
        this(16);
    }

    public SipHashMap(int size) {
        ensureCapacity(size);
    }

    public SipHashMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    public SipHashMap(Map<K, V> map) {
        this.putAll(map);
    }

    @Override
    protected void resize() {
        super.resize();
        mask = length - 1;
    }

    @Override
    protected int indexFor(K id) {
        if (hash == null) {
            int v;
            return ((v = id.hashCode()) ^ (v >>> 16)) & mask;
        }
        return (int) hash.digest(id) & mask;
    }

    @Override
    public Entry<K, V> getOrCreateEntry(K id) {
        Entry<K, V> entry = getEntryFirst(id, true);
        if (entry.v == NOT_USING)
            return entry;
        int i = 15;
        while (true) {
            if (Objects.equals(id, entry.k))
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;

            if (i-- == 0) {
                new Throwable("[Warning] Hash collision that SIP must be used").printStackTrace();
                hash = new SipHash();
                hash.setKeyDefault();
                super.resize();

                entry = getEntryFirst(id, true);
            } else if (i < -10) {
                throw new AssertionError();
            }
        }
        Entry<K, V> firstUnused = getCachedEntry(id);
        entry.next = firstUnused;
        return firstUnused;
    }
}
