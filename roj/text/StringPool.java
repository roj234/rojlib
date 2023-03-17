package roj.text;

import roj.collect.IntBiMap;
import roj.util.ByteList;

import java.io.IOException;
import java.io.OutputStream;
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

	public StringPool(ByteList reader) {
		int length = reader.readVarInt(false);
		String[] array = new String[length];
		this.ordered = Arrays.asList(array);
		for (int i = 0; i < length; i++) {
			array[i] = reader.readVarIntUTF();
		}
	}

	public ByteList writePool(ByteList data) {
		data.putVarInt(ordered.size(), false);
		for (String s : ordered) {
			data.putVarIntUTF(s);
		}
		return data;
	}

	public ByteList writeString(ByteList w, String string) {
		int id = list.getInt(string);
		if (id == -1) {
			list.putByValue(id = list.size(), string);
			ordered.add(string);
		}
		return w.putVarInt(id, false);
	}

	public String readString(ByteList r) {
		return ordered.get(r.readVarInt(false));
	}

	public void writePool(OutputStream os) throws IOException {
		writePool(new ByteList(5 * ordered.size())).writeToStream(os);
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
