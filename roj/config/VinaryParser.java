package roj.config;

import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2022/2/6 9:15
 */
public final class VinaryParser implements BinaryParser, BiConsumer<String, Object> {
	private static final byte[] ENCODE_MAP = new byte[256], DECODE_MAP = new byte[256];
	private static final int TINY_ID_COUNT = createMap(Type.VALUES.length, ENCODE_MAP, DECODE_MAP);
	private static final byte STUDY = -2, LONG = -1;
	private static int createMap(int count, byte[] encode, byte[] decode) {
		int i = 0;

		// 列表(n0)中低四位的空余
		for (int subType = count; subType < 16; subType++) {
			int b = (subType << 4);
			if (encode != null) encode[i] = (byte) b;
			i++;
			decode[b] = (byte) i;
		}

		// 其它，同时不满足列表(n0)和有效元素(0n)
		for (int mainType = 1; mainType < 16; mainType++) {
			for (int subType = mainType >= count ? 0 : 1; subType < 16; subType++) {
				int b = (subType << 4) | mainType;
				if (b >= 254) continue;

				if (encode != null) encode[i] = (byte) b;
				i++;
				decode[b] = (byte) i;
			}
		}

		decode[254] = STUDY;
		decode[255] = LONG;
		return i;
	}

	private static final class Struct {
		String[] names;
		int id, hash;

		Struct next;
	}

	private Struct[] tab;
	private int mask, size;

	private String[] tmp;
	private int matches;

	private CVisitor cc;
	private final List<String[]> decodeTab = new SimpleList<>();
	private byte[] decodeMap = DECODE_MAP;
	private int tinyIdCount;

	private boolean asArray;

	public VinaryParser() {
		this.tab = new Struct[16];
		this.mask = 15;
		this.tmp = new String[20];
	}

	public VinaryParser asArray() {
		asArray = true;
		return this;
	}

	@Override
	public <T extends CVisitor> T parseRaw(InputStream in, T cc, int flag) throws IOException {
		decodeTab.clear();
		this.cc = cc;
		try {
			element(IOUtil.getSharedByteBuf().readStreamFully(in));
		} finally {
			this.cc = null;
		}
		return cc;
	}

	@Override
	public <T extends CVisitor> T parseRaw(DynByteBuf buf, T cc, int flag) throws IOException {
		decodeTab.clear();
		this.cc = cc;
		try {
			element(buf);
		} finally {
			this.cc = null;
		}
		return cc;
	}

	private void element(DynByteBuf r) throws IOException {
		int b = r.readUnsignedByte();

		int rid = decodeMap[b];
		if (rid != 0) {
			String[] arr;
			if (rid == STUDY) {
				if (decodeTab.isEmpty()) {
					int var = r.readUnsignedByte();
					if (var != Type.VALUES.length) {
						tinyIdCount = createMap(var, null, decodeMap = new byte[256]);
					} else {
						decodeMap = DECODE_MAP;
						tinyIdCount = TINY_ID_COUNT;
					}
				}

				arr = new String[r.readVUInt()+1];
				for (int i = 0; i < arr.length; i++) {
					arr[i] = r.readZhCn();
				}
				decodeTab.add(arr);
			} else if (rid == LONG) {
				arr = decodeTab.get(r.readVUInt()+tinyIdCount);
			} else {
				arr = decodeTab.get((rid-1)&0xFF);
			}

			cc.valueMap(arr.length);
			for (String s : arr) {
				cc.key(s);
				element(r);
			}
			cc.pop();
			return;
		}

		switch (Type.VALUES[b & 0xF]) {
			case LIST: parseList(b >>> 4, r); break;
			case MAP:
				int size = r.readVUInt();
				cc.valueMap(size);
				while (size-- > 0) {
					cc.key(r.readZhCn());
					element(r);
				}
				cc.pop();
				break;
			case STRING: cc.value(r.readZhCn()); break;
			case NULL: cc.valueNull(); break;
			case BOOL: cc.value(r.readBoolean()); break;
			case INTEGER: cc.value(r.readInt()); break;
			case LONG: cc.value(r.readLong()); break;
			case DOUBLE: cc.value(r.readDouble()); break;
			case Int1: cc.value(r.readByte()); break;
			case Int2: cc.value(r.readShort()); break;
			case Float4: cc.value(r.readFloat()); break;
			default: throw new IllegalArgumentException("Unexpected id " + b);
		}
	}
	private void parseList(int type, DynByteBuf r) throws IOException {
		int cap = r.readVUInt();
		if (type == 0) {
			cc.valueList(cap);
			while (cap-- > 0) element(r);
		} else {
			Type value = Type.VALUES[--type];

			if (!asArray || (value != Type.INTEGER && value != Type.LONG && value != Type.Int1)) {
				cc.valueList(cap);
			}

			switch (value) {
				case BOOL:
					int bi = 0;
					while (cap-- > 0) {
						int bit = ((r.get(r.rIndex) << (bi & 0x7)) >>> 7) & 0x1;

						cc.value(bit != 0);

						if (++bi == 8) {
							r.rIndex ++;
							bi = 0;
						}
					}
					break;
				case MAP:
					while (cap-- > 0) {
						int size = r.readVUInt();
						cc.valueMap(size);
						while (size-- > 0) {
							cc.key(r.readZhCn());
							element(r);
						}
						cc.pop();
					}
					break;
				case INTEGER: {
					if (asArray) {
						int[] data = new int[cap];
						int i = 0;
						while (cap-- > 0) data[i++] = r.readInt();
						cc.value(data);
						return;
					} else {
						while (cap-- > 0) cc.value(r.readInt());
					}
				}
				break;
				case NULL: while (cap-- > 0) cc.valueNull(); break;
				case LONG: {
					if (asArray) {
						long[] data = new long[cap];
						int i = 0;
						while (cap-- > 0) data[i++] = r.readLong();
						cc.value(data);
						return;
					} else {
						while (cap-- > 0) cc.value(r.readLong());
					}
				}
				break;
				case DOUBLE: while (cap-- > 0) cc.value(r.readDouble()); break;
				case STRING: while (cap-- > 0) cc.value(r.readZhCn()); break;
				case Int1: {
					if (asArray) {
						byte[] data = new byte[cap];
						r.read(data);
						cc.value(data);
						return;
					} else {
						while (cap-- > 0) cc.value(r.readByte());
					}
				}
				break;
				case Int2: while (cap-- > 0) cc.value(r.readShort()); break;
				case Float4: while (cap-- > 0) cc.value(r.readFloat()); break;
				default: throw new IllegalArgumentException("Unsupported type");
			}
		}
		cc.pop();
	}

	public void serialize(CEntry entry, DynByteBuf out) throws IOException { entry._toBinary(out, this); }

	public void reset() {
		if (size > 0) {
			size = 0;
			Arrays.fill(tab, null);
		}
	}

	public String format() { return "Vinary"; }

	public String[] tryCompress(Map<String, CEntry> map, DynByteBuf w) {
		if (map.isEmpty()) return null;

		matches = 0;
		map.forEach(this);
		int len = matches;

		String[] m = tmp;
		Arrays.sort(m, 0, len);

		int hash = 1;
		for (int i = 0; i < len; i++) hash = 31 * hash + m[i].hashCode();

		Struct entry = tab[hash & mask];
		Struct prev = null;
		while (entry != null) {
			find:
			if (entry.names.length == len) {
				String[] a = entry.names;
				for (int i = 0; i < len; i++) {
					if (!a[i].equals(m[i])) break find;
				}

				int id = entry.id;
				if (id < TINY_ID_COUNT) {
					w.put(ENCODE_MAP[id]);
				} else {
					w.put(LONG).putVUInt(id-TINY_ID_COUNT);
				}

				return entry.names;
			}
			prev = entry;
			entry = entry.next;
		}

		entry = new Struct();
		entry.id = size;
		entry.hash = hash;
		entry.names = new String[len];
		System.arraycopy(m, 0, entry.names, 0, len);

		if (prev == null) tab[hash & mask] = entry;
		else prev.next = entry;

		if (++size > tab.length << 1) expand();

		w.write(STUDY);
		if (size == 1) w.write(Type.VALUES.length);

		w.putVUInt(len-1);
		for (int i = 0; i < len; i++) w.putZhCn(m[i]);

		return entry.names;
	}
	private void expand() {
		Struct[] newTab = new Struct[tab.length << 1];
		int mask = this.mask = newTab.length - 1;

		Struct[] tab = this.tab;
		for (Struct s : tab) {
			while (s != null) {
				Struct o = newTab[s.hash & mask];
				newTab[s.hash & mask] = s;

				Struct next = s.next;
				s.next = o;
				s = next;
			}
		}

		this.tab = newTab;
	}
	public void accept(String s, Object e) {
		String[] m = tmp;
		m[matches++] = s;
		if (matches == m.length) {
			tmp = new String[m.length+10];
			System.arraycopy(m, 0, tmp, 0, m.length);
		}
	}
}
