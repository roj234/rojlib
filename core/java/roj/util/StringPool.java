package roj.util;

import roj.collect.ToIntMap;
import roj.io.ByteInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StringPool {
	private final ToIntMap<String> index;
	private final List<String> values;

	public StringPool(ByteInput w) throws IOException {
		int len = w.readVUInt();
		String[] array = new String[len];
		this.index = null;
		this.values = Arrays.asList(array);
		for (int i = 0; i < len; i++) {
			array[i] = w.readVUIGB();
		}
	}
	public String get(ByteInput r) throws IOException { return values.get(r.readVUInt()); }

	public StringPool() { values = new ArrayList<>(); index = new ToIntMap<>(); }

	public DynByteBuf add(DynByteBuf w, String s) { return w.putVUInt(add(s)); }
	public int add(String s) {
		int id = index.getOrDefault(s, -1);
		if (id < 0) {
			index.putInt(s, id = index.size());
			values.add(s);
		}
		return id;
	}

	public DynByteBuf writePool(DynByteBuf w) {
		w.putVUInt(values.size());
		for (int i = 0; i < values.size(); i++) w.putVUIGB(values.get(i));
		return w;
	}
}