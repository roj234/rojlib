package roj.collect;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: TimedHashMap.java.java
 */
public class TimedHashMap<K, V> extends MyHashMap<K, V> {
    protected long timeout, lastCheck;

    boolean selfRemove, update;

    public TimedHashMap(int timeOut) {
        this.timeout = (update = timeOut < 0) ? -timeOut : timeOut;
    }

    List<TimedEntry<K, V>> list = new ArrayList<>();

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
    void afterChange(K key, V original, V now, Entry<K, V> entry) {
        super.afterChange(key, original, now, entry);
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
        final TimedEntry<?, ?> entry1 = (TimedEntry<?, ?>) entry;
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
