package roj.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/5/16 14:17
 */
public class ThreadLocalNG<T>  {
    private final int hashCode = nextHashCode();
    private static AtomicInteger nextHashCode = new AtomicInteger();
    private static final int HASH_INCREMENT = 0x61c88647;
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        return (T) ThreadAllocCleaner.getOrCreateMap().get(this);
    }

    public void remove() {
        ThreadAllocCleaner.getOrCreateMap().remove(this);
    }

    public void set(T value) {
        ThreadAllocCleaner.getOrCreateMap().put(this, value);
    }
}
