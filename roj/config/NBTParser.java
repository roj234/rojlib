package roj.config;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.exch.*;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT IO class
 *
 * @see <a href="https://github.com/udoprog/c10t/blob/master/docs/NBT.txt">Online NBT specification</a>
 */
public final class NBTParser {
	public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

	public static CMapping parse(InputStream is) throws IOException {
		return parse((DataInput) (is instanceof DataInputStream ? is : new DataInputStream(is)));
	}

	public static CMapping parse(DataInput in) throws IOException {
		byte flg = in.readByte();
		if (flg != COMPOUND) throw new IOException("Topmost entry must be a COMPOUND");
		char n = in.readChar();
		if (n != 0) throw new IOException("Topmost entry must not have name");
		return read0(in, flg).asMap();
	}

	private static CEntry read0(DataInput in, byte type) throws IOException {
		switch (type) {
			case END:
			default: throw new IOException("Corrupted NBT");
			case BYTE: return TByte.valueOf(in.readByte());
			case SHORT: return TShort.valueOf(in.readShort());
			case INT: return CInteger.valueOf(in.readInt());
			case LONG: return CLong.valueOf(in.readLong());
			case FLOAT: return TFloat.valueOf(in.readFloat());
			case DOUBLE: return CDouble.valueOf(in.readDouble());
			case BYTE_ARRAY:
				byte[] ba = new byte[in.readInt()];
				in.readFully(ba);
				return new TByteArray(ba);
			case STRING: return CString.valueOf(in.readUTF());
			case LIST:
				byte listType = in.readByte();
				int len = in.readInt();
				SimpleList<CEntry> lo = new SimpleList<>(len);
				while (len-- > 0) lo.add(read0(in, listType));
				return new CList(lo);
			case COMPOUND:
				MyHashMap<String, CEntry> tags = new MyHashMap<>();
				do {
					byte flg = in.readByte();
					if (flg == 0) break;
					tags.put(in.readUTF(), read0(in, flg));
				} while (true);
				return new CMapping(tags);
			case INT_ARRAY:
				int[] ia = new int[in.readInt()];
				for (int i = 0; i < ia.length; i++) ia[i] = in.readInt();
				return new TIntArray(ia);
			case LONG_ARRAY:
				long[] la = new long[in.readInt()];
				for (int i = 0; i < la.length; i++) la[i] = in.readLong();
				return new TLongArray(la);
		}
	}
}
