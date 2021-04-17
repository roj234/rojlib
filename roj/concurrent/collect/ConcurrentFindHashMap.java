package roj.concurrent.collect;

import roj.collect.FindMap;
import roj.collect.MyHashMap;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/20 14:03
 */
public class ConcurrentFindHashMap<K, V> implements FindMap<K, V> {
    private final AtomicInteger lock = new AtomicInteger();

    private final MyHashMap<K, V> map = new MyHashMap<>();

    /**
     * 只要没人修改即可
     */
    private void waitForGet() {
        if (lock.get() == 2)
            return;
        while (!lock.compareAndSet(0, 2))
            Thread.yield();
    }

    /**
     * 而这个只能一个人修改
     */
    private void waitForUpdate() {
        while (!lock.compareAndSet(0, 1))
            Thread.yield();
    }

    public ConcurrentFindHashMap() {
    }

    @Override
    public Entry<K, V> find(K k) {
        waitForGet();
        Entry<K, V> entry = map.find(k);
        lock.set(0);
        return entry;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        waitForGet();
        boolean c = map.containsKey(key);
        lock.set(0);
        return c;
    }

    @Override
    public boolean containsValue(Object value) {
        waitForGet();
        boolean c = map.containsValue(value);
        lock.set(0);
        return c;
    }

    @Override
    public V get(Object key) {
        waitForGet();
        V v = map.get(key);
        lock.set(0);
        return v;
    }

    @Override
    public V put(K key, V value) {
        waitForUpdate();
        V v = map.put(key, value);
        lock.set(0);
        return v;
    }

    @Override
    public V remove(Object key) {
        waitForUpdate();
        V v = map.remove(key);
        lock.set(0);
        return v;
    }

    @Override
    public void putAll(@Nonnull Map<? extends K, ? extends V> m) {
        waitForUpdate();
        map.putAll(m);
        lock.set(0);
    }

    @Override
    public void clear() {
        waitForUpdate();
        map.clear();
        lock.set(0);
    }

    @Nonnull
    @Override
    public Set<K> keySet() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Collection<V> values() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new ConcurrentModificationException();
    }
}
