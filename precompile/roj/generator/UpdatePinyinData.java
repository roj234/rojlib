package roj.generator;

import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMA2Writer;
import roj.collect.IntList;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.GB18030;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/12/22 0022 16:31
 */
class UpdatePinyinData {
	public static void run(String pinyinData, String phrasePinyinData) throws Exception {
		TrieTree<String[]> pinyinMap = new TrieTree<>();
		try (TextReader in = TextReader.auto(new File(pinyinData))) {
			CharList sb = IOUtil.getSharedCharBuf();
			Pattern p = Pattern.compile("^U\\+([0-9A-F]+): (.+)  #");
			while (in.readLine(sb)) {
				if (sb.startsWith("#")) {
					sb.clear(); continue;
				}

				Matcher matcher = p.matcher(sb);
				boolean matches = matcher.find();
				if (!matches) {
					System.out.println("no match "+sb);
					sb.clear();
					continue;
				}
				int cp = Integer.parseInt(matcher.group(1), 16);
				List<String> avlPinyin = TextUtil.split(matcher.group(2), ',');

				sb.clear();
				pinyinMap.put(sb.appendCodePoint(cp).toString(), avlPinyin.toArray(new String[0]));
				sb.clear();
			}
		}
		try (TextReader in = TextReader.auto(new File(phrasePinyinData))) {
			CharList sb = IOUtil.getSharedCharBuf();
			while (in.readLine(sb)) {
				if (sb.startsWith("#")) {
					sb.clear();continue;
				}
				int pos = sb.indexOf(": ");
				CharSequence hanzi = sb.subSequence(0, pos);
				List<String> pinyin = TextUtil.split(sb.subSequence(pos+1, sb.length()), ' ');
				String string = hanzi.toString();
				String myst = string;
				while (pinyinMap.containsKey(myst)) myst+="|";
				PrimitiveIterator.OfInt iterator = string.codePoints().iterator();
				int i = 0;
				while (iterator.hasNext()) {
					int cp = iterator.nextInt();
					sb.clear();
					String s = sb.appendCodePoint(cp).toString();
					String[] myPinyin = pinyinMap.get(s);
					SimpleList<String> list;
					if (myPinyin == null) {
						myPinyin = new String[]{""};
						list = new SimpleList<>();
					} else {
						list = SimpleList.asModifiableList(myPinyin);
					}
					if (!myPinyin[0].equals(pinyin.get(i))) {
						pinyinMap.put(myst, pinyin.toArray(new String[0]));
					}
					if (!list.contains(pinyin.get(i))) {
						list.add(pinyin.get(i));
						pinyinMap.put(s, list.toArray(new String[0]));
					}
					i++;
				}
				sb.clear();
			}
		}
		System.out.println("size="+pinyinMap.size());
		SimpleList<Map.Entry<CharSequence, String[]>> map = new SimpleList<>(pinyinMap.entrySet());
		map.sort((o1, o2) -> Integer.compare(o2.getKey().length(), o1.getKey().length()));
		xx:
		for (Map.Entry<CharSequence, String[]> entry : map) {
			List<String> pinyin = Arrays.asList(entry.getValue());
			PrimitiveIterator.OfInt iterator = entry.getKey().codePoints().iterator();
			CharList sb = IOUtil.getSharedCharBuf();

			int i = 0;
			int min = -1, max = -1;
			while (iterator.hasNext()) {
				int cp = iterator.nextInt();
				sb.clear();
				String pi = sb.appendCodePoint(cp).toString();
				String[] myPinyin = pinyinMap.get(pi);
				if (myPinyin == null) {
					System.err.println("Missing single-word pinyin for "+pi+"("+cp+") in"+entry.getKey());
					break;
				}
				if (!myPinyin[0].equals(pinyin.get(i))) {
					if (min == -1) min = i;
					max = i+1;
				}
				i++;
			}
			if (max == min && entry.getKey().codePoints().count() > 1) {
				System.out.println("full same "+entry.getKey());
			}
			if (max-min <= 1) continue;
			if (min == 0 && max == entry.getKey().codePoints().count()) continue;

			int[] array = entry.getKey().codePoints().skip(min).limit(max - min).toArray();
			sb.clear();
			for (int i1 : array) sb.appendCodePoint(i1);

			CharSequence sequence = sb.toString();
			String[] entry1 = pinyinMap.get(sequence);
			if (entry1 != null) {
				for (int j = min; j < max; j++) {
					if (!entry1[j-min].equals(pinyin.get(j))) {
						continue xx;
					}
				}
				System.out.println("partial match "+entry.getKey()+"["+pinyin.subList(min, max)+"] in "+sequence+"["+Arrays.asList(entry1)+"]");
				pinyinMap.remove(entry.getKey());
			}
		}
		map = new SimpleList<>(pinyinMap.entrySet());
		map.sort((o2, o1) -> {
			int v = Long.compare(o1.getKey().codePoints().count(), o2.getKey().codePoints().count());
			return v != 0 ? v : o1.getKey().toString().compareTo(o2.getKey().toString());
		});

		int mys = 0, mys2 = 0;
		EasyProgressBar bar = new EasyProgressBar("ConstantPool");
		bar.addMax(map.size());
		IntList offset = new IntList();
		ByteList bb = new ByteList();
		CharList ziPool = new CharList();
		CharList yinPool = new CharList();
		int yinLen = 0, nameLen = 0;
		for (Map.Entry<CharSequence, String[]> entry : map) {
			int off = ziPool.indexOf(entry.getKey());
			if (off < 0) {
				off = ziPool.length();
				ziPool.append(entry.getKey());
			} else {
				mys += entry.getKey().length();
			}
			offset.add(off);
			nameLen = Math.max(entry.getKey().length(), nameLen);

			String[] value = entry.getValue();
			CharList sb = new CharList();
			for (String s : value) sb.append(delTone(new CharList(s)));
			off = yinPool.indexOf(sb);
			if (off < 0) {
				off = yinPool.length();
				yinPool.append(sb);
			} else {
				mys2 += sb.length();
			}
			offset.add(off);
			yinLen = Math.max(sb.length(), yinLen);
			value[0] = sb.toString();
			bar.addCurrent(1);
		}
		bar.end();
		bar.reset();
		int ybits = Integer.numberOfTrailingZeros(MathUtils.getMin2PowerOf(yinLen));
		int nbits = Integer.numberOfTrailingZeros(MathUtils.getMin2PowerOf(nameLen));
		System.out.println("cpSize="+ziPool.length()+",shortZi="+mys+",shortYin="+mys2);
		System.out.println(yinLen+"=>"+ybits);
		System.out.println(nameLen+"=>"+nbits);

		LZMA2Options options = new LZMA2Options(9).setDictSize(524288);

		// 让压缩率再高一点吧~
		try (OutputStream out = options.getOutputStream(new FileOutputStream(Main.resourcePath.getAbsolutePath()+ "/roj/text/JPinyin.lzma"))) {
			LZMA2Writer myOut = (LZMA2Writer) out;

			bb.clear();
			bb.putVUInt(GB18030.CODER.byteCount(ziPool)).putVUInt(ziPool.length())
			  .putVUInt(GB18030.CODER.byteCount(yinPool)).putVUInt(yinPool.length())
			  .putVUInt(map.size()).put(nbits).put(ybits);
			bb.writeToStream(out);

			bb.clear();
			bb.putGBData(ziPool).putGBData(yinPool);

			int size0 = options.findBestProps(bb.toByteArray());
			myOut.setProps(options);
			bb.writeToStream(out);
			System.out.println("PROP0="+options+", string pool="+size0);

			bb.clear();
			int j = 0;
			for (Map.Entry<CharSequence, String[]> entry : map) {
				int off = offset.get(j++);
				bb.putInt((off << nbits) | entry.getKey().length());

				off = offset.get(j++);
				bb.putInt((off << ybits) | entry.getValue()[0].length());
			}

			int size1 = options.findBestProps(bb.toByteArray());
			myOut.setProps(options);
			bb.writeToStream(out);
			System.out.println("PROP1="+options+", offset data="+size1);

			System.out.println("total size is "+(size0+size1));
		}
	}
	private static CharList delTone(CharList sb) {
		sb.replace('ü', 'v');
		int rpc=0;
		rpc=sb.preg_replace_callback(Pattern.compile("[àèìòùǜǹ]"), m -> {
			int i = m.pattern().pattern().indexOf(m.group(0));
			return String.valueOf("[aeioun]".charAt(i));
		});
		if (rpc != 0) {
			sb.append('4');
			return sb;
		}
		rpc=sb.preg_replace_callback(Pattern.compile("[ǎăĕěĭǐŏǒŭǔǚň]"), m -> {
			int i = m.pattern().pattern().indexOf(m.group(0));
			return String.valueOf("[aaeeiioouuun]".charAt(i));
		});
		if (rpc != 0) {
			sb.append('3');
			return sb;
		}
		rpc=sb.preg_replace_callback(Pattern.compile("[áéíóúǘń]"), m -> {
			int i = m.pattern().pattern().indexOf(m.group(0));
			return String.valueOf("[aeiouun]".charAt(i));
		});
		if (rpc != 0) {
			sb.append('2');
			return sb;
		}
		rpc=sb.preg_replace_callback(Pattern.compile("[āēīōūǖ]"), m -> {
			int i = m.pattern().pattern().indexOf(m.group(0));
			return String.valueOf("[aeiouu]".charAt(i));
		});
		if (rpc != 0) {
			sb.append('1');
			return sb;
		}
		for (int i = 0; i < sb.length(); i++) {
			if (sb.list[i] > 127) System.err.println("Unprocessable U+"+Integer.toHexString(sb.list[i]));
		}
		sb.append('0');
		return sb;
	}
}