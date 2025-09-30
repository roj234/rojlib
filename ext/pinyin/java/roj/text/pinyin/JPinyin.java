package roj.text.pinyin;

import roj.archive.xz.LZMA2InputStream;
import roj.collect.ArrayList;
import roj.collect.TrieEntry;
import roj.collect.TrieTree;
import roj.compiler.runtime.SwitchMapI;
import roj.config.node.IntValue;
import roj.io.ByteInputStream;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.FastCharset;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2020/9/30 20:51
 */
public class JPinyin {
	private static String[] FIRST_TONE, LAST_TONE;
	private static int toneBits, toneMask;
	private static byte[] TONE_POOL;
	private static final TrieTree<Integer> PinyinWords = new TrieTree<>();
	private static final SwitchMapI FastSwitch;

	static {
		// 数据生成见roj.datagen.MakePinyinData
		// 内部数据格式可能随时改动
		try (var in = ByteInputStream.wrap(new LZMA2InputStream(JPinyin.class.getClassLoader().getResourceAsStream("roj/text/pinyin/JPinyin.lzma"), 65536+32768))) {
			FIRST_TONE = new String[in.readUnsignedByte()+1];
			FIRST_TONE[0] = "";
			LAST_TONE = new String[in.readUnsignedByte()+1];

			for (int j = 1; j < FIRST_TONE.length; j++) {
				FIRST_TONE[j] = in.readVUIStr(FastCharset.UTF16BE());
			}
			for (int j = 1; j < LAST_TONE.length; j++) {
				LAST_TONE[j] = in.readVUIStr(FastCharset.UTF16BE());
			}

			int tonePoolLength = in.readInt();

			int skip = 4 - ((int)in.position() & 3);
			if (skip != 4) in.skipBytes(skip);

			TONE_POOL = in.readBytes(tonePoolLength);

			int newOffset = (int) in.position();

			int count = in.readInt();
			toneBits = in.readInt();
			toneMask = (1 << toneBits) - 1;

			for (int i = 0; i < count; i++) {
				String key = in.readVUIStr(FastCharset.UTF16BE());

				PinyinWords.put(key, in.readInt() - (2 << toneBits));

				skip = 4 - ((int)(in.position()-newOffset) & 3);
				if (skip != 4) in.skipBytes(skip);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		var map = SwitchMapI.Builder.builder(6);
		map.add('a', 0);
		map.add('e', 1);
		map.add('i', 2);
		map.add('o', 3);
		map.add('u', 4);
		map.add('v', 5);
		FastSwitch = map.build();
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

	private final IntValue myMatchLen = new IntValue();

	public JPinyin() {}

	public static final int PINYIN_FIRST_LETTER = 0;
	public static final int PINYIN_TONE = 1;
	public static final int PINYIN_TONE_NUMBER = 2;
	public static final int PINYIN_NONE = 3;
	public static final int PINYIN_MULTI = 4;

	public String toPinyin(CharSequence s) { return toPinyin(s, "", IOUtil.getSharedCharBuf(), PINYIN_NONE).toString(); }
	public String toPinyin(CharSequence s, int mode) { return toPinyin(s, "", IOUtil.getSharedCharBuf(), mode).toString(); }
	public CharList toPinyin(CharSequence str, String splitter, CharList sb, int mode) {
		int i = 0, len = str.length();
		if (len == 0) return sb;

		boolean hasSplitter = false;
		while (i < len) {
			Integer match = PinyinWords.match(str, i, len, myMatchLen);

			int matchLen = myMatchLen.value;
			if (matchLen > 0) {
				if (!hasSplitter) sb.append(splitter);
				addTone(sb, match, matchLen > 1 ? mode|PINYIN_MULTI : mode, splitter);
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
		int off = cpState & toneMask;
		int len = off + (cpState >>> toneBits);
		while (true) {
			int first = TONE_POOL[off]&0xFF;
			int last = TONE_POOL[off+1]&0xFF;
			switch (mode & 3) {
				case PINYIN_TONE -> addTone(sb, first, last);
				case PINYIN_FIRST_LETTER -> sb.append((first == 0 ? LAST_TONE[last] : FIRST_TONE[first]).charAt(0));
				default/*case PINYIN_TONE_NUMBER*/ -> sb.append(FIRST_TONE[first]).append(LAST_TONE[last]);
				case PINYIN_NONE -> sb.append(FIRST_TONE[first]).append(LAST_TONE[last], 0, LAST_TONE[last].length()-1);
			}
			if ((mode & PINYIN_MULTI) == 0) break;

			if (off == len) break;
			off += 2;

			sb.append(splitter);
		}
	}
	private static void addTone(CharList sb, int first, int last) {
		String vowels = null;

		int start = sb.length();

		sb.append(FIRST_TONE[first]).append(LAST_TONE[last]);
		char vowelIndex = sb.charAt(sb.length() - 1);
		if (vowelIndex > '9') return;

		sb.setLength(sb.length()-1);
		int end = sb.length();

		findProperVowel:
		for (int i = start; i < end; i++) {
			// 从前往后查找第一个a,e或ou
			// 若不存在则从后往前查找i,o,u,v
			switch (FastSwitch.get(sb.charAt(i))) {
				case 0: vowels = "aāáăà"; start = i; break findProperVowel;
				case 1: vowels = "eēéĕè"; start = i; break findProperVowel;
				case 2: vowels = "iīíĭì"; start = i; break;
				case 3: vowels = "oōóŏò"; start = i; if (i+1 < end && sb.charAt(i+1) == 'u') break findProperVowel; break;
				case 4: vowels = "uūúŭù"; start = i; break;
				case 5: vowels = "üǖǘǚǜ"; start = i; sb.set(i, 'ü'); break;
			}
		}
		// replace v
		for (int j = start; j < end; j++) {
			if (sb.charAt(j) == 'v') sb.set(j, 'ü');
		}

		assert vowels != null : "invalid StringPool";
		sb.set(start, vowels.charAt(vowelIndex-'0'));
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
			Integer match = PinyinWords.match(str, i, len, myMatchLen);

			int matchLen = myMatchLen.value;
			if (matchLen > 0) {
				if (sb.length() > 0) {
					out.add(new String[] {sb.toString()});
					sb.clear();
				}

				tmp.add(str.subSequence(i, i+matchLen).toString());
				if (matchLen == 1) addTone2(tmp, match);
				else {
					addTone(sb, match, PINYIN_NONE| PINYIN_MULTI, "");
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
		int off = cpState & toneMask;
		int len = off + (cpState >>> toneBits);
		while (true) {
			CharList sb = IOUtil.getSharedCharBuf();
			sb.append(FIRST_TONE[TONE_POOL[off]]).append(LAST_TONE[TONE_POOL[off+1]]);
			char vowelIndex = sb.charAt(sb.length() - 1);
			if (vowelIndex <= '9') sb.setLength(sb.length()-1);
			tmp.add(sb.toString());

			if (off == len) break;
			off += 2;
		}
	}
}