package roj.config;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.exch.TByte;
import roj.config.exch.TFloat;
import roj.config.exch.TShort;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static roj.config.serial.Structs.DECODE_MAP;
import static roj.config.serial.Structs.TINY_ID_COUNT;

/**
 * @author Roj233
 * @since 2022/5/17 2:14
 */
public final class MCfgParser {
	String[][] tab;

	public void read(DynByteBuf b) {
		int len = b.readInt();
		if (tab == null || tab.length < len) tab = new String[len][];
		else Arrays.fill(tab, null);

		int i = 0;
		while (i < len) {
			String[] a = new String[b.readVarInt(false) + 1];
			for (int j = 0; j < a.length; j++) {
				a[j] = b.readVIVIC();
			}
			tab[i++] = a;
		}
	}

	private CMapping tryDecode(int rid, DynByteBuf r) {
		if (rid != 255 && DECODE_MAP[rid] == 0) return null;

		if (rid == 255) {
			rid = r.readVarInt(false) + TINY_ID_COUNT;
		} else {
			rid = (DECODE_MAP[rid]&0xFF)-1;
		}

		String[] s;
		try {
			s = tab[rid];
		} catch (Exception e) {
			throw new IllegalArgumentException("Corrupted data: unknown struct #" + rid);
		}

		MyHashMap<String, CEntry> ent = new MyHashMap<>(s.length);
		ent.ensureCapacity(s.length);
		for (String value : s) ent.put(value, parse(r, this));

		return new CMapping(ent);
	}

	public static CEntry parse(DynByteBuf r) {
		return parse(r, null);
	}

	public static CEntry parse(DynByteBuf r, MCfgParser dcmp) {
		int b = r.readUnsignedByte();
		if (dcmp != null) {
			CMapping m = dcmp.tryDecode(b, r);
			if (m != null) return m;
		}

		switch (Type.VALUES[b & 0xF]) {
			case LIST: return parseList(b >>> 4, r, dcmp);
			case MAP:
				int cap = r.readVarInt(false);
				Map<String, CEntry> map = new MyHashMap<>(cap);
				while (cap-- > 0) map.put(r.readVIVIC(), parse(r, dcmp));
				return new CMapping(map);
			case STRING: return CString.valueOf(r.readVIVIC());
			case NULL: return CNull.NULL;
			case BOOL: return CBoolean.valueOf(r.readBoolean());
			case INTEGER: return CInteger.valueOf(r.readInt());
			case LONG: return CLong.valueOf(r.readLong());
			case DOUBLE: return CDouble.valueOf(r.readDouble());
			case Int1: return TByte.valueOf(r.readByte());
			case Int2: return TShort.valueOf(r.readShort());
			case Float4: return TFloat.valueOf(r.readFloat());
			default: throw new IllegalArgumentException("Unexpected id " + b);
		}
	}

	private static CList parseList(int type, DynByteBuf r, MCfgParser dcmp) {
		int cap = r.readVarInt(false);
		List<CEntry> list = new SimpleList<>(cap);
		if (type == 0) {
			while (cap-- > 0) list.add(parse(r, dcmp));
		} else {
			switch (Type.VALUES[--type]) {
				case BOOL:
					int bi = 0;
					while (cap-- > 0) {
						int bit = ((r.get(r.rIndex) << (bi & 0x7)) >>> 7) & 0x1;

						list.add(CBoolean.valueOf(bit != 0));

						if (++bi == 8) {
							r.rIndex ++;
							bi = 0;
						}
					}
					break;
				case INTEGER: while (cap-- > 0) list.add(CInteger.valueOf(r.readInt())); break;
				case NULL: while (cap-- > 0) list.add(CNull.NULL); break;
				case MAP:
					while (cap-- > 0) {
						if (dcmp != null) {
							int rid = r.readUnsignedByte();
							if (rid != Type.MAP.ordinal()) {
								CMapping m = dcmp.tryDecode(rid, r);
								if (m == null) throw new IllegalArgumentException("Illegal struct descriptor");
								list.add(m);
								continue;
							}
						}

						int cap1 = r.readVarInt(false);
						Map<String, CEntry> map = new MyHashMap<>(cap1);
						while (cap1-- > 0) map.put(r.readVIVIC(), MCfgParser.parse(r, dcmp));

						list.add(new CMapping(map));
					}
					break;
				case LONG: while (cap-- > 0) list.add(CLong.valueOf(r.readLong()));break;
				case DOUBLE: while (cap-- > 0) list.add(CDouble.valueOf(r.readDouble())); break;
				case STRING: while (cap-- > 0) list.add(CString.valueOf(r.readVIVIC())); break;
				case Int1: while (cap-- > 0) list.add(TByte.valueOf(r.readByte())); break;
				case Int2: while (cap-- > 0) list.add(TShort.valueOf(r.readShort())); break;
				case Float4: while (cap-- > 0) list.add(TFloat.valueOf(r.readFloat())); break;

				case LIST:
				default: throw new IllegalArgumentException("Unsupported type");
			}
		}
		return new CList(list);
	}
}
