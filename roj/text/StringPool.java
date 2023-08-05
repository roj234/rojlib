package roj.text;

import roj.collect.IntBiMap;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StringPool {
	final IntBiMap<String> list = new IntBiMap<>();
	final List<String> ordered;
	long length;

	public StringPool() { ordered = new ArrayList<>(); }

	public StringPool(DynByteBuf w) {
		int len = w.readVUInt();
		String[] array = new String[len];
		this.ordered = Arrays.asList(array);
		for (int i = 0; i < len; i++) {
			array[i] = w.readVUIGB();
		}
	}

	public DynByteBuf writePool(DynByteBuf w) {
		w.putVUInt(ordered.size());
		for (int i = 0; i < ordered.size(); i++) w.putVUIGB(ordered.get(i));
		return w;
	}

	public DynByteBuf add(DynByteBuf w, String s) { return w.putVUInt(add(s)); }
	public String get(DynByteBuf r) { return ordered.get(r.readVUInt()); }

	public int size() { return ordered.size(); }
	public long length() { return length; }

	public int add(String s) {
		int id = list.getInt(s);
		if (id == -1) {
			length += s.length();
			list.putByValue(id = list.size(), s);
			ordered.add(s);
		}
		return id;
	}
}
