package roj.mapper.util;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;

import java.util.Collection;
import java.util.Iterator;

/**
 * Mapper List
 *
 * @author Roj234
 * @since 2020/8/11 14:59
 */
public final class MapperList extends SimpleList<String> {
	private final ToIntMap<String> indexer;

	public MapperList() {
		this(16);
	}

	public MapperList(int size) {
		ensureCapacity(size);
		indexer = new ToIntMap<>(size);
	}

	@Override
	public boolean remove(Object o) {
		if (!indexer.isEmpty()) throw new UnsupportedOperationException();
		return super.remove(o);
	}

	@Override
	public int indexOf(Object o) {
		return indexer.getOrDefault(o, -1);
	}

	@Override
	public int lastIndexOf(Object o) {
		return indexer.getOrDefault(o, -1);
	}

	@Override
	public boolean contains(Object e) {
		return indexer.containsKey(e);
	}

	@Override
	public void clear() {
		if (!indexer.isEmpty()) throw new UnsupportedOperationException();
		super.clear();
	}

	@Override
	public boolean addAll(int index, Collection<? extends String> collection) {
		if (collection.isEmpty()) return false;

		ensureCapacity(size + collection.size());
		if (size != index && size > 0) System.arraycopy(list, index, list, index + collection.size(), size - index);

		Iterator<? extends String> it = collection.iterator();
		for (int j = index; j < index + collection.size(); j++) {
			String next = it.next();
			if (indexOf(next) == -1) list[j] = next;
		}
		size += collection.size();
		return true;
	}

	public void _init_() {
		preClean();
		trimToSize();
	}

	public void preClean() {
		indexer.clear();
		for (int i = 0; i < size; i++) {
			Integer orig = indexer.putInt((String) list[i], i);
			if (orig != null) {
				super.remove((int) orig);
			} else if (list[i] == null) {
				size = i;
				break;
			} else {
				continue;
			}
			i = 0;
			indexer.clear();
		}
	}
}
