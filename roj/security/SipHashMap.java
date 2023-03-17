package roj.security;

import roj.collect.MyHashMap;

import java.util.Map;
import java.util.Objects;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author solo6975
 * @since 2022/3/21 14:37
 */
public class SipHashMap<K extends CharSequence, V> extends MyHashMap<K, V> {
	private SipHash hash;

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
	protected int hash(K id) {
		if (hash == null) {
			int v;
			return ((v = id.hashCode()) ^ (v >>> 16));
		}

		long v = hash.digest(id);
		return ((int) (v >>> 32) ^ (int) v);
	}

	@Override
	public Entry<K, V> getOrCreateEntry(K id) {
		Entry<K, V> entry = getEntryFirst(id, true);
		if (entry.v == UNDEFINED) return entry;
		int i = 4;
		while (true) {
			if (Objects.equals(id, entry.k)) return entry;
			if (entry.next == null) break;
			entry = entry.next;

			if (--i == 0) {
				if (hash != null) throw new AssertionError();

				new Throwable("[Warning] Hash collision that SIP must be used").printStackTrace();
				hash = new SipHash();
				hash.setKeyDefault();
				super.resize();

				entry = getEntryFirst(id, true);
				i = 4;
			}
		}
		Entry<K, V> firstUnused = getCachedEntry(id);
		entry.next = firstUnused;
		return firstUnused;
	}
}
