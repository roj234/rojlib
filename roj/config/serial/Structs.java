package roj.config.serial;

import roj.collect.MyHashMap;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.data.Type;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 结构压缩组件: 在map的key中寻找统一部份
 *
 * @author Roj234
 * @since 2022/2/6 9:15
 */
public class Structs implements BiConsumer<String, Object> {
	public static final int STRUCT_VER = Type.VALUES.length;

	public static final byte[] ENCODE_MAP, DECODE_MAP;
	public static final int TINY_ID_COUNT;
	static {
		ENCODE_MAP = new byte[256];
		DECODE_MAP = new byte[256];
		int k = 0;

		// 列表
		for (int subType = STRUCT_VER+1; subType < 16; subType++) {
			int b = (subType << 4);
			ENCODE_MAP[k++] = (byte) b;
			DECODE_MAP[b] = (byte) k;
		}

		// 其它
		for (int mainType = 1; mainType < 16; mainType++) {
			for (int subType = mainType >= STRUCT_VER ? 0 : 1; subType < 16; subType++) {
				int b = (subType << 4) | mainType;
				ENCODE_MAP[k++] = (byte) b;
				DECODE_MAP[b] = (byte) k;
			}
		}

		TINY_ID_COUNT = k;
	}

	public static Structs newCompressor() {
		return new Structs();
	}

	static final class Struct {
		String[] names;
		int id, hash;

		Struct sibling;
	}

	public ByteList p0;

	Struct[] tab;
	int mask, size;

	String[] tmp;
	int matches, hit;

	// this is not so easy / WIP
	boolean partialMatch;

	private Comparator<String> cmp;

	public Structs() {
		this.tab = new Struct[16];
		this.mask = 15;
		this.tmp = new String[20];
		this.p0 = new ByteList(256).putInt(0);
	}

	public int hit() {
		return hit;
	}

	public int size() {
		return size;
	}

	public void setComparator(Comparator<String> cmp) {
		this.cmp = cmp;
	}

	public ByteList finish() {
		return p0.putInt(0, size);
	}

	public boolean tryCompress(CMapping map, DynByteBuf w) {
		if (mask == 0) throw new IllegalStateException("Not at write state");

		Map<String, CEntry> intl = map.raw();
		if (intl.isEmpty()) return false;

		int len = match(intl);
		String[] m = this.tmp;
		int hash = 1;
		for (int i = 0; i < len; i++) {
			hash = 31 * hash + m[i].hashCode();
		}

		if (size > tab.length << 1) expand();

		Struct s = tab[hash & mask];
		if (s == null) {
			tab[hash & mask] = s = put(m, len, hash);
			size++;
		} else {
			while (true) {
				find:
				if (s.names.length == len) {
					String[] a = s.names;
					for (int i = 0; i < len; i++) {
						if (!a[i].equals(m[i])) break find;
					}
					hit++;
					break;
				}
				if (s.sibling == null) {
					s = s.sibling = put(m, len, hash);
					size++;
					break;
				}
				s = s.sibling;
			}
		}

		int rid = s.id;
		if (rid < TINY_ID_COUNT) {
			w.put(ENCODE_MAP[rid]);
		} else {
			w.put((byte) 255).putVarInt(rid - TINY_ID_COUNT, false);
		}

		m = s.names;
		for (int i = 0; i < len; i++) {
			intl.get(m[i]).toBinary(w, this);
		}
		return true;
	}

	private void expand() {
		Struct[] newTab = new Struct[tab.length << 1];
		int mask = this.mask = newTab.length - 1;

		Struct[] tab = this.tab;
		for (Struct s : tab) {
			while (s != null) {
				Struct o = newTab[s.hash & mask];
				newTab[s.hash & mask] = s;

				Struct next = s.sibling;
				s.sibling = o;
				s = next;
			}
		}

		this.tab = newTab;
	}

	private int match(Map<String, ?> map) {
		int len;
		if (map instanceof MyHashMap) {
			matches = 0;
			map.forEach(this);
			len = matches;
		} else {
			String[] m = tmp;
			len = 0;
			for (String s : map.keySet()) {
				m[len++] = s;
				if (len == m.length) {
					tmp = new String[m.length + 10];
					System.arraycopy(m, 0, tmp, 0, m.length);
					m = tmp;
				}
			}
		}
		Arrays.sort(tmp, 0, len, cmp);
		return len;
	}

	private Struct put(String[] m, int len, int hash) {
		Struct s = new Struct();
		s.id = size;
		s.hash = hash;
		s.names = new String[len];
		System.arraycopy(m, 0, s.names, 0, len);

		p0.putVarInt(len - 1, false);
		for (int i = 0; i < len; i++) {
			p0.putVarIntVIC(m[i]);
		}
		return s;
	}

	@Override
	public void accept(String s, Object e) {
		String[] m = Structs.this.tmp;
		m[matches++] = s;
		if (matches == m.length) {
			tmp = new String[m.length + 10];
			System.arraycopy(m, 0, tmp, 0, m.length);
		}
	}

	@Override
	public String toString() {
		return "Structs{" + "size=" + size + ", hit=" + hit + '}';
	}
}
