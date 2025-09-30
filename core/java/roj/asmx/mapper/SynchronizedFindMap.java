package roj.asmx.mapper;

import roj.collect.HashMap;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 注意：它现在只是一个保证Mapper线程安全的最小实现
 * @author Roj234
 * @since 2020/8/20 14:03
 */
final class SynchronizedFindMap<K, V> extends HashMap<K, V> {
	private final ReentrantLock lock = new ReentrantLock();

	public SynchronizedFindMap() {}

	@Override
	protected AbstractEntry<K, V> myCreateEntry(K key, V val) {
		try {
			lock.lock();
			return super.myCreateEntry(key, val);
		} finally {
			lock.unlock();
		}
	}
}