package roj.text;

import roj.collect.IntBiMap;
import roj.util.ByteList;
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

	public StringPool() {
		this.ordered = new ArrayList<>();
	}

	public StringPool(DynByteBuf w) {
		int length = w.readVUInt();
		String[] array = new String[length];
		this.ordered = Arrays.asList(array);
		for (int i = 0; i < length; i++) {
			array[i] = w.readVUIUTF();
		}
	}

	public DynByteBuf writePool(DynByteBuf w) {
		w.putVUInt(ordered.size());
		for (int i = 0; i < ordered.size(); i++) w.putVUIUTF(ordered.get(i));
		return w;
	}

	public DynByteBuf writeString(DynByteBuf w, String string) {
		int id = list.getInt(string);
		if (id == -1) {
			list.putByValue(id = list.size(), string);
			ordered.add(string);
		}
		return w.putVUInt(id);
	}

	public String readString(ByteList r) {
		return ordered.get(r.readVUInt());
	}

	public int size() {
		return ordered.size();
	}

	public int add(String string) {
		int id = list.getInt(string);
		if (id == -1) {
			list.putByValue(id = list.size(), string);
			ordered.add(string);
			return id;
		}
		return -1;
	}
}
