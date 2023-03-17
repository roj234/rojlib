package roj.text;

import roj.collect.IntBiMap;
import roj.util.ByteList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author solo6975
 * @since 2021/9/30 22:42
 */
public class DottedStringPool extends StringPool {
	final char delimChar;
	final IntBiMap<String> dottedList = new IntBiMap<>();

	public DottedStringPool(char c) {
		this.delimChar = c;
	}

	public DottedStringPool(ByteList r, char c) {
		super(r);
		this.delimChar = c;
	}

	private List<String> tmp;

	public ByteList writeDlm(ByteList w, String string) {
		if (tmp == null) {tmp = new ArrayList<>();} else tmp.clear();
		int id = dottedList.getInt(string);
		if (id != -1) {
			return w.putVarInt(id, false);
		}
		List<String> clipped = TextUtil.split(tmp, string, delimChar);
		w.put((byte) 0).put((byte) clipped.size());
		for (int i = 0; i < clipped.size() - 1; i++) {
			String string1;
			id = list.getInt(string1 = clipped.get(i));
			if (id == -1) {
				list.putByValue(id = list.size(), string1);
				ordered.add(string1);
			}
			w.putVarInt(id, false);
		}
		w.putVarIntUTF(clipped.get(clipped.size() - 1));
		dottedList.putByValue(dottedList.size() + 1, string);
		return w;
	}

	private CharList tmp2;

	public String readDlm(ByteList r) {
		int blocks = r.readVarInt(false);
		if (blocks == 0) {
			blocks = r.readUnsignedByte() - 1;
			CharList cl = tmp2;
			if (cl == null) {cl = tmp2 = new CharList();} else cl.clear();
			for (int j = 0; j < blocks; j++) {
				cl.append(ordered.get(r.readVarInt(false))).append(delimChar);
			}
			cl.append(r.readVarIntUTF());
			String e = cl.toString();
			dottedList.putByValue(dottedList.size() + 1, e);
			return e;
		} else {
			return dottedList.get(blocks);
		}
	}
}
