package roj.asmx.mapper;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2020/8/11 14:59
 */
final class MapperList extends SimpleList<String> {
	private final ToIntMap<String> index;
	public int selfIdx = -1;

	public MapperList() { this(8); }
	public MapperList(int size) {
		ensureCapacity(size);
		index = new ToIntMap<>(size);
	}

	public int indexOf(Object o) { return index.getOrDefault(o, -1); }
	public int lastIndexOf(Object o) { return index.getOrDefault(o, -1); }
	public boolean contains(Object e) { return index.containsKey(e); }

	public String remove(int index) { throw new UnsupportedOperationException(); }
	public void clear() { throw new UnsupportedOperationException(); }

	public void pack0() { selfIdx = size; }
	public void index() {
		index.clear();
		index.ensureCapacity(size);
		for (int i = 0; i < size; i++) index.putInt(list[i].toString(), i);
	}

	public void batchAddFiltered(List<String> list) {
		if (list.isEmpty()) return;

		int len = list instanceof MapperList ? ((MapperList) list).selfIdx : list.size();
		ensureCapacity(size+len);

		for (int i = 0; i < len; i++) {
			String s = list.get(i);
			// ?
			if (s == null) continue;

			if (index.putIntIfAbsent(s, size)) {
				this.list[size++] = s;
			}
		}
	}
	public void batchRemoveFiltered(Map<String, String> filter) {
		boolean mod = false;
		for (int i = size-1; i >= 0; i--) {
			if (!filter.containsKey(list[i].toString())) {
				if (i < selfIdx) selfIdx--;
				super.remove(i);
				mod = true;
			}
		}
		if (mod) index();
	}
}