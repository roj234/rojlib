package roj.text;

import roj.archive.qz.xz.LZMAInputStream;
import roj.collect.*;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.util.ByteList;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2020/9/30 20:51
 */
public class JPinyin {
	private static final TrieTree<Integer> WordsFrequency = new TrieTree<>();
	public static List<String> splitWord(String input) {
		loadMPDict();
		int i = 0, len = input.length();
		List<String> out = new SimpleList<>();
		while (i < len) {
			Map.Entry<MutableInt, Integer> spec = WordsFrequency.longestMatches(input, i, len), prob, prob2 = null;
			if (spec != null) {
				int to = i+spec.getKey().getValue();
				int probIdx = i, probIdx2 = 0;
				for (int j = to+2; j >= i+1; j--) {
					prob = WordsFrequency.longestMatches(input, j, len);
					if (prob == null) continue;

					boolean b = WordsFrequency.containsKey(input, i, j);

					if (j < to && prob.getValue() > spec.getValue() && prob.getKey().getValue() >= spec.getKey().getValue()) {
						spec = prob;
						probIdx = j;
					} else if (b && j+prob.getKey().getValue() >= to) {
						spec = prob2 = prob;
						probIdx = probIdx2 = j;
					}
				}

				if (prob2 != null) {
					spec = prob2;
					probIdx = probIdx2;
				}

				if (probIdx > i) out.add(input.substring(i, probIdx));
				out.add(input.substring(probIdx, probIdx+=spec.getKey().getValue()));
				i = probIdx;
			} else {
				out.add(String.valueOf(input.charAt(i++)));
			}
		}
		return out;
	}
	private static void loadMPDict() {
		if (!WordsFrequency.isEmpty()) return;
		// https://github.com/yanyiwu/cppjieba
		try (InputStream in = new LZMAInputStream(JPinyin.class.getResourceAsStream("/META-INF/china/mp_dict.lzma"))) {
			ByteList bb = new ByteList().readStreamFully(in);
			int priority = 0;
			while (bb.isReadable()) {
				int len = bb.readInt();
				for (int i = 0; i < len; i++) {
					String key = bb.readVUIGB();
					WordsFrequency.put(key, priority);
				}
				priority++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final MyBitSet IsHanzi = new MyBitSet(0xFFFF);
	private static char[] StringPool;
	// also can use a modified version of ToIntMap<String>
	private static final TrieTree<Integer> PinyinWords = new TrieTree<>();
	private static final Int2IntMap FastSwitch = new Int2IntMap(8);
	static {
		// https://github.com/mozillazg/pinyin-data
		try (InputStream in = new LZMAInputStream(JPinyin.class.getResourceAsStream("/META-INF/china/pinyin.lzma"))) {
			ByteList bb = new ByteList().readStreamFully(in);

			int cpLen = bb.readVUInt();
			CharList out = new CharList(cpLen);
			GB18030.CODER.decodeFixedIn(bb,cpLen, out);
			StringPool = out.toCharArray();

			CharList sb = IOUtil.getSharedCharBuf();
			int len = bb.readInt();
			while (len-- > 0) {
				int codepoint = bb.readVUInt();
				if (codepoint <= 0xFFFF) IsHanzi.add(codepoint);
				int yinLen = bb.readVUInt();
				sb.clear();
				PinyinWords.put(sb.appendCodePoint(codepoint).toString(), yinLen);
			}

			len = bb.readInt();
			while (len-- > 0) {
				int ww = bb.readVUInt();
				String word = new String(StringPool,ww >>> 5, ww & 31);
				int yinLen = bb.readVUInt();
				PinyinWords.put(word, yinLen);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		PinyinWords.compact();

		FastSwitch.put('a', 0);
		FastSwitch.put('e', 1);
		FastSwitch.put('i', 2);
		FastSwitch.put('o', 3);
		FastSwitch.put('u', 4);
		FastSwitch.put('v', 5);
	}

	public static MyBitSet getIsHanzi() { return IsHanzi; }

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
		int off = cpState >>> 5;
		int len = off + (cpState & 31);
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
