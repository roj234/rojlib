package roj.plugins.pinyin;

import roj.archive.xz.LZMA2InputStream;
import roj.collect.*;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.util.DynByteBuf;

import java.nio.CharBuffer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2020/9/30 20:51
 */
public class JPinyin {
	private static char[] StringPool;
	private static int CPBits, CPMask;
	private static final TrieTree<Integer> PinyinWords = new TrieTree<>();
	private static final Int2IntMap FastSwitch = new Int2IntMap(8);
	static {
		// https://github.com/mozillazg/pinyin-data
		try (LZMA2InputStream in = new LZMA2InputStream(JPinyin.class.getClassLoader().getResourceAsStream("roj/plugins/pinyin/JPinyin.lzma"), 524288)) {
			int DATALEN = 1432238;
			var bb = DynByteBuf.allocateDirect(DATALEN);
			int i = in.read(bb.address(), DATALEN);
			bb.wIndex(i);
			assert i == DATALEN;

			int zcpLenByte = bb.readVUInt(), zcpLen = bb.readVUInt();
			int ycpLenByte = bb.readVUInt(), ycpLen = bb.readVUInt(), mapSize = bb.readVUInt();
			int zbits = bb.readUnsignedByte(), ybits = bb.readUnsignedByte();

			int mask = (1 << zbits) - 1;
			CharBuffer zcp = CharBuffer.allocate(zcpLen);
			FastCharset.GB18030().decodeFixedIn(bb,zcpLenByte,zcp);
			char[] cp = zcp.array();

			CPBits = ybits;
			CPMask = (1 << ybits) - 1;
			CharBuffer ycp = CharBuffer.allocate(ycpLen);
			FastCharset.GB18030().decodeFixedIn(bb,ycpLenByte,ycp);
			StringPool = ycp.array();

			while (mapSize-- > 0) {
				int zref = bb.readInt();
				int yref = bb.readInt();
				String key = new String(cp, zref >>> zbits, zref & mask);
				PinyinWords.put(key, yref);
			}

			bb._free();
		} catch (Exception e) {
			e.printStackTrace();
		}

		FastSwitch.put('a', 0);
		FastSwitch.put('e', 1);
		FastSwitch.put('i', 2);
		FastSwitch.put('o', 3);
		FastSwitch.put('u', 4);
		FastSwitch.put('v', 5);
	}
	/**
	 * 这个字是中文吗
	 */
	public static boolean isChinese(int c) {
		TrieEntry node = JPinyin.getPinyinWords().getRoot();
		if (Character.isSupplementaryCodePoint(c)) {
			node = node.getChild(Character.highSurrogate(c));
			if (node == null) return false;
			node = node.getChild(Character.lowSurrogate(c));
		} else {
			node = node.getChild((char) c);
		}
		return node != null && node.isLeaf();
	}

	private static final JPinyin instance = new JPinyin();
	public static JPinyin getInstance() { return instance; }

	public static TrieTree<Integer> getPinyinWords() { return PinyinWords; }

	private final HashMap.Entry<CInt, Integer> entry = new HashMap.Entry<>(new CInt(), null);

	public JPinyin() {}

	public static final int PINYIN_FIRST_LETTER = 0;
	public static final int PINYIN_TONE = 1;
	public static final int PINYIN_TONE_NUMBER = 2;
	public static final int PINYIN_NONE = 3;
	public static final int PINYIN_DUOYINZI = 4;

	public String toPinyin(CharSequence s) { return toPinyin(s, "", IOUtil.getSharedCharBuf(), PINYIN_NONE).toString(); }
	public String toPinyin(CharSequence s, int mode) { return toPinyin(s, "", IOUtil.getSharedCharBuf(), mode).toString(); }
	public CharList toPinyin(CharSequence str, String splitter, CharList sb, int mode) {
		int i = 0, len = str.length();
		if (len == 0) return sb;

		boolean hasSplitter = false;
		while (i < len) {
			PinyinWords.match(str, i, len, entry);

			int matchLen = entry.getKey().value;
			if (matchLen > 0) {
				if (!hasSplitter) sb.append(splitter);
				addTone(sb, entry.getValue(), matchLen > 1 ? mode|PINYIN_DUOYINZI : mode, splitter);
				hasSplitter = true;
				i += matchLen;
			} else {
				sb.append(str.charAt(i++));
				hasSplitter = false;
			}
		}
		if (hasSplitter) sb.setLength(sb.length() - splitter.length());
		return sb;
	}
	private static void addTone(CharList sb, int cpState, int mode, String splitter) {
		int off = cpState >>> CPBits;
		int len = off + (cpState & CPMask);
		int j = findNextNumber(off);
		while (true) {
			switch (mode & 3) {
				case PINYIN_TONE -> addTone(sb, off, j);
				case PINYIN_FIRST_LETTER -> sb.append(StringPool[off]);
				default/*case PINYIN_TONE_NUMBER*/ -> sb.append(StringPool, off, j + 1);
				case PINYIN_NONE -> sb.append(StringPool, off, j);
			}
			if ((mode & PINYIN_DUOYINZI) == 0) break;

			off = j+1;
			j = findNextNumber(off);
			if (j+1 > len) break;

			sb.append(splitter);
		}
	}
	private static int findNextNumber(int i) {
		while (i < StringPool.length) {
			if (Tokenizer.NUMBER.contains(StringPool[i])) return i;
			i++;
		}
		return i;
	}
	private static void addTone(CharList sb, int from, int to) {
		String vowels = null;

		int offset = sb.length()-from;
		sb.append(StringPool, from, to);

		findProperVowel:
		for (int i = from; i < to; i++) {
			// 从前往后查找第一个a,e或ou
			// 若不存在则从后往前查找i,o,u,v
			switch (FastSwitch.getOrDefaultInt(StringPool[i], -1)) {
				case 0: vowels = "aāáăà"; from = i; break findProperVowel;
				case 1: vowels = "eēéĕè"; from = i; break findProperVowel;
				case 2: vowels = "iīíĭì"; from = i; break;
				case 3: vowels = "oōóŏò"; from = i; if (StringPool[i+1] == 'u') break findProperVowel; break;
				case 4: vowels = "uūúŭù"; from = i; break;
				case 5: vowels = "üǖǘǚǜ"; from = i; sb.set(offset+i, 'ü'); break;
			}
		}
		// replace v
		for (int j = from; j < to; j++) {
			if (StringPool[j] == 'v') sb.set(offset+j, 'ü');
		}

		assert vowels != null : "invalid StringPool";
		char vowel = vowels.charAt(StringPool[to]-'0');
		sb.set(offset+from, vowel);
	}

	public static Comparator<CharSequence> pinyinSorter() {
		JPinyin instance = JPinyin.getInstance();
		return (o1, o2) -> instance.toPinyin(o1).compareTo(instance.toPinyin(o2));
	}

	public List<String[]> toChoices(CharSequence str) {
		int i = 0, len = str.length();

		var out = new ArrayList<String[]>();
		var sb = IOUtil.getSharedCharBuf();
		var tmp = new LinkedHashSet<String>();

		while (i < len) {
			PinyinWords.match(str, i, len, entry);

			int matchLen = entry.getKey().value;
			if (matchLen > 0) {
				if (sb.length() > 0) {
					out.add(new String[] {sb.toString()});
					sb.clear();
				}

				tmp.add(str.subSequence(i, i+matchLen).toString());
				if (matchLen == 1) addTone2(tmp, entry.getValue());
				else {
					addTone(sb, entry.getValue(), PINYIN_NONE|PINYIN_DUOYINZI, "");
					tmp.add(sb.toString());
					sb.clear();
				}
				out.add(tmp.toArray(new String[tmp.size()]));
				tmp.clear();

				i += matchLen;
			} else {
				sb.append(str.charAt(i++));
			}
		}

		if (sb.length() > 0) {
			out.add(new String[] {sb.toString()});
			sb.clear();
		}
		return out;
	}
	private static void addTone2(Set<String> tmp, int cpState) {
		int off = cpState >>> CPBits;
		int len = off + (cpState & CPMask);
		int j = findNextNumber(off);
		do {
			tmp.add(new String(StringPool, off, j - off));
			off = j+1;
			j = findNextNumber(off);
		} while (j+1 <= len);
	}
}