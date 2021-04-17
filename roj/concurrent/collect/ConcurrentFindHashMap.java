package roj.concurrent.collect;

import roj.collect.FindMap;
import roj.collect.MyHashMap;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj234
 * @since 2020/8/20 14:03
 */
public class ConcurrentFindHashMap<K, V> extends MyHashMap<K, V> implements FindMap<K, V> {
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public ConcurrentFindHashMap() {}

	@Override
	public void ensureCapacity(int size) {
		try {
			if (lock != null) lock.writeLock().lock();
			super.ensureCapacity(size);
		} finally {
			if (lock != null) lock.writeLock().unlock();
		}
	}

	@Override
	public MyHashMap.Entry<K, V> getEntry(K id) {
		try {
			lock.readLock().lock();
			return super.getEntry(id);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public MyHashMap.Entry<K, V> getOrCreateEntry(K id) {
		try {
			lock.writeLock().lock();
			return super.getOrCreateEntry(id);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public boolean containsValue(Object value) {
		try {
			lock.readLock().lock();
			return super.containsValue(value);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	protected V remove0(Object k, Object v) {
		try {
			lock.writeLock().lock();
			return super.remove0(k, v);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void putAll(@Nonnull Map<? extends K, ? extends V> m) {
		try {
			lock.writeLock().lock();
			super.putAll(m);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void clear() {
		try {
			lock.writeLock().lock();
			super.clear();
		} finally {
			lock.writeLock().unlock();
		}
	}
}
