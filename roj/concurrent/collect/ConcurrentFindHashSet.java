package roj.concurrent.collect;

import roj.collect.FindSet;
import roj.collect.MyHashSet;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/20 14:03
 */
public class ConcurrentFindHashSet<T> implements FindSet<T> {
    private final AtomicInteger lock = new AtomicInteger();

    private final MyHashSet<T> set = new MyHashSet<>();

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

    public ConcurrentFindHashSet(Collection<T> list) {
        addAll(list);
    }

    @Override
    public T find(T t) {
        waitForGet();
        t = set.find(t);
        lock.set(0);
        return t;
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object key) {
        waitForGet();
        boolean c = set.contains(key);
        lock.set(0);
        return c;
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        waitForGet();
        Object[] o = set.toArray();
        lock.set(0);
        return o;
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        waitForGet();
        T1[] t1 = set.toArray(a);
        lock.set(0);
        return t1;
    }

    @Override
    public boolean add(T key) {
        waitForUpdate();
        boolean b = set.add(key);
        lock.set(0);
        return b;
    }

    @Override
    public boolean remove(Object key) {
        waitForUpdate();
        boolean b = set.remove(key);
        lock.set(0);
        return b;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        waitForGet();
        boolean b = set.containsAll(c);
        lock.set(0);
        return b;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends T> m) {
        waitForUpdate();
        boolean b = set.addAll(m);
        lock.set(0);
        return b;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        waitForUpdate();
        boolean b = set.retainAll(c);
        lock.set(0);
        return b;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        waitForUpdate();
        boolean b = set.removeAll(c);
        lock.set(0);
        return b;
    }

    @Override
    public void clear() {
        waitForUpdate();
        set.clear();
        lock.set(0);
    }
}
