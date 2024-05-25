package roj.text;

import roj.collect.ToIntMap;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StringPool {
	private final ToIntMap<String> list;
	private final List<String> ordered;

	public StringPool(DynByteBuf w) {
		int len = w.readVUInt();
		String[] array = new String[len];
		this.list = null;
		this.ordered = Arrays.asList(array);
		for (int i = 0; i < len; i++) {
			array[i] = w.readVUIGB();
		}
	}
	public String get(DynByteBuf r) { return ordered.get(r.readVUInt()); }

	public StringPool() { ordered = new ArrayList<>(); list = new ToIntMap<>(); }

	public DynByteBuf add(DynByteBuf w, String s) { return w.putVUInt(add(s)); }
	public int add(String s) {
		int id = list.getOrDefault(s, -1);
		if (id < 0) {
			list.putInt(s, id = list.size());
			ordered.add(s);
		}
		return id;
	}

	public DynByteBuf writePool(DynByteBuf w) {
		w.putVUInt(ordered.size());
		for (int i = 0; i < ordered.size(); i++) w.putVUIGB(ordered.get(i));
		return w;
	}
}