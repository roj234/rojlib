package roj.concurrent.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/5/27 0027 1:51
 */
public class IsolationMap<K, V> {
	private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	private static final long
		NEXT_OFF = ReflectionUtils.fieldOffset(Entry.class, "next"),
		VALUE_OFF = ReflectionUtils.fieldOffset(Entry.class, "v"),
		SIZE_OFF = ReflectionUtils.fieldOffset(IsolationMap.class, "size");
	private static final Entry<?, ?> SENTIAL = new Entry<>(null, null);

	public static final class Entry<K, V> extends WeakReference<K> {
		final int hash;
		Entry<K, V> next;

		public Entry(ReferenceQueue<?> queue, K k) {
			super(k, Helpers.cast(queue));
			hash = System.identityHashCode(k);
		}

		public volatile V v;
		public V getValue() { return v; }
	}

	private void doEvict() {
		Entry<?, ?>[] array = entries;
		Entry<?, ?> remove;
		while ((remove = (Entry<?, ?>) queue.poll()) != null) {
			int i = remove.hash & (array.length - 1);

			loop:
			for (;;) {
				var entry = array[i];
				if (entry == null) continue;

				Entry<?, ?> prev = null;
				for (;;) {
					if (entry == remove) {
						Object next = u.getAndSetObject(entry, NEXT_OFF, SENTIAL);
						if (next != SENTIAL) {
							if (prev == null) array[i] = (Entry<?, ?>) next;
							else prev.next = Helpers.cast(next);

							u.getAndAddInt(this, SIZE_OFF, -1);
						}

						break loop;
					}

					prev = entry;
					entry = entry.next;
				}
			}
		}
	}

	private Entry<?, ?>[] entries;
	private volatile int size;
	private int length = 1;

	public IsolationMap() { this(4); }
	public IsolationMap(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		Entry<?, ?>[] newEntries = new Entry<?, ?>[length];
		int newMask = length-1;

		int i = 0, j = entries.length;
		for (; i < j; i++) {
			Entry<K, V> entry = (Entry<K, V>) entries[i];
			if (entry == null) continue;

			do {
				Entry<K, V> next = entry.next;
				int newKey = entry.hash & newMask;
				entry.next = (Entry<K, V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry = next;
			} while (entry != null);
		}

		entries = newEntries;
	}

	@SuppressWarnings("unchecked")
	public Entry<K, V> getEntry(K key) {
		lock.readLock().lock();
		try {
			doEvict();

			Entry<?, ?>[] array = entries;
			Entry<K, V> entry = array == null ? null : (Entry<K, V>) array[System.identityHashCode(key)&(array.length - 1)];

			while (entry != null) {
				if (key == entry.get()) return entry;
				entry = entry.next;
			}
		} finally {
			lock.readLock().unlock();
		}
		return null;
	}

	public final V computeIfAbsent(K key, @NotNull Function<? super K, ? extends V> mapper) {
		if (size == length) {
			lock.writeLock().lock();
			try {
				if (size == length)
					resize();
			} finally {
				lock.writeLock().unlock();
			}
		}

		Entry<K, V> entry;
		lock.readLock().lock();
		try {
			doEvict();

			entry = getOrCreateEntry(key);
		} finally {
			lock.readLock().unlock();
		}

		while (true) {
			V value = entry.v;
			if (value != null) return value;

			if (u.compareAndSwapObject(entry, VALUE_OFF, null, SENTIAL)) {
				u.getAndAddInt(this, SIZE_OFF, 1);

				entry.v = value = mapper.apply(key);
				return value;
			}
		}
	}

	public final void clear() {
		if (size == 0) return;

		lock.writeLock().lock();

		size = 0;
		Arrays.fill(entries, null);
		doEvict();

		lock.writeLock().unlock();
	}

	@SuppressWarnings("unchecked")
	private Entry<K, V> getOrCreateEntry(K key) {
		Entry<?, ?>[] array = entries;
		if (array == null) {
			lock.readLock().unlock();
			lock.writeLock().lock();
			try {
				array = entries;
				if (array == null) entries = array = new Entry<?, ?>[length];
			} finally {
				lock.writeLock().unlock();
				lock.readLock().lock();
			}
		}

		int i = System.identityHashCode(key)&(array.length-1);
		Entry<K, V> entry;
		long offset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + ((long) i * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
		// CAS first
		for (;;) {
			for (;;) {
				entry = (Entry<K, V>) u.getObjectVolatile(array, offset);
				if (entry != null) break;

				if (u.compareAndSwapObject(array, offset, null, SENTIAL)) {
					u.putObjectVolatile(array, offset, entry = new Entry<>(queue, key));
					return entry;
				}
			}

			while (entry != SENTIAL) {
				if (key == entry.get()) return entry;

				if (entry.next == null) {
					if (u.compareAndSwapObject(entry, NEXT_OFF, null, SENTIAL)) {
						Entry<K, V> next = new Entry<>(queue, key);
						entry.next = next;
						return next;
					}
				}

				entry = entry.next;
			}
		}
	}
}