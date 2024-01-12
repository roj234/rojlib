package roj.text;

import roj.archive.qz.xz.LZMA2InputStream;
import roj.collect.Int2IntMap;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.util.DirectByteList;

import java.nio.CharBuffer;

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
		try (LZMA2InputStream in = new LZMA2InputStream(JPinyin.class.getResourceAsStream("/META-INF/china/pinyin.lzma2"), 524288)) {
			int DATALEN = 1432238;
			DirectByteList bb = DirectByteList.allocateDirect(DATALEN);
			int i = in.read(bb.address(), DATALEN);
			bb.wIndex(i);
			assert i == DATALEN;

			int zcpLenByte = bb.readVUInt(), zcpLen = bb.readVUInt();
			int ycpLenByte = bb.readVUInt(), ycpLen = bb.readVUInt(), mapSize = bb.readVUInt();
			int zbits = bb.readUnsignedByte(), ybits = bb.readUnsignedByte();

			int mask = (1 << zbits) - 1;
			CharBuffer zcp = CharBuffer.allocate(zcpLen);
			GB18030.CODER.decodeFixedIn(bb,zcpLenByte,zcp);
			char[] cp = zcp.array();

			CPBits = ybits;
			CPMask = (1 << ybits) - 1;
			CharBuffer ycp = CharBuffer.allocate(ycpLen);
			GB18030.CODER.decodeFixedIn(bb,ycpLenByte,ycp);
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

	public static TrieTree<Integer> getPinyinWords() { return PinyinWords; }

	private final MyHashMap.Entry<MutableInt, Integer> entry = new MyHashMap.Entry<>(new MutableInt(), null);
	public JPinyin() {}

	public static final int PINYIN_FIRST_LETTER = 0;
	public static final int PINYIN_TONE = 1;
	public static final int PINYIN_TONE_NUMBER = 2;
	public static final int PINYIN_NONE = 3;
	public static final int PINYIN_DUOYINZI = 4;

	public String toPinyin(CharSequence s) { return toPinyin(s, "", IOUtil.getSharedCharBuf(), PINYIN_NONE).toString(); }
	public String toPinyin(CharSequence s, int mode) { return toPinyin(s, " ", IOUtil.getSharedCharBuf(), mode).toString(); }
	public CharList toPinyin(CharSequence str, String splitter, CharList sb, int mode) {
		int i = 0, len = str.length();
		if (len == 0) return sb;

		boolean hasSplitter = false;
		while (i < len) {
			PinyinWords.match(str, i, len, entry);

			int matchLen = entry.getKey().getValue();
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
		while (off < len) {
			int j = findNextNumber(off);
			switch (mode&3) {
				case PINYIN_TONE: addTone(sb, off, j); break;
				case PINYIN_FIRST_LETTER: sb.append(StringPool[off]); break;
				default: case PINYIN_TONE_NUMBER: sb.append(StringPool, off, j+1); break;
				case PINYIN_NONE: sb.append(StringPool, off, j); break;
			}
			off = j+1;
			sb.append(splitter);
			if ((mode&PINYIN_DUOYINZI) == 0) break;
		}
	}
	private static int findNextNumber(int i) {
		while (true) {
			if (ITokenizer.NUMBER.contains(StringPool[i])) return i;
			i++;
		}
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
}