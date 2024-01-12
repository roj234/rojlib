package roj.concurrent.collect;

import org.jetbrains.annotations.NotNull;
import roj.collect.FindMap;
import roj.collect.MyHashMap;

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
	public AbstractEntry<K, V> getEntry(K key) {
		try {
			lock.readLock().lock();
			return super.getEntry(key);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public AbstractEntry<K, V> getOrCreateEntry(K key) {
		try {
			lock.writeLock().lock();
			return super.getOrCreateEntry(key);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public AbstractEntry<K, V> getValueEntry(Object value) {
		try {
			lock.readLock().lock();
			return super.getValueEntry(value);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	protected AbstractEntry<K, V> remove0(Object k, Object v) {
		try {
			lock.writeLock().lock();
			return super.remove0(k, v);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void putAll(@NotNull Map<? extends K, ? extends V> m) {
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