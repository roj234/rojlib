package roj.config;

import roj.collect.MyHashMap;
import roj.text.CharList;
import roj.text.LineReader;

import java.util.Map;

/**
 * Localization Util
 *
 * @author solo6975
 * @since 2021/6/17 23:43
 */
public class I18n {
	public static final I18n NULL = new I18n("");

	public static Map<String, String> parseLang(CharSequence content) {
		MyHashMap<String, String> map = new MyHashMap<>();
		if (content == null) return map;
		try {
			for (String line : LineReader.create(content)) {
				line = line.trim();
				if (line.startsWith("#") || line.isEmpty()) continue;

				int i = line.indexOf('=');
				String k = line.substring(0, i++);
				map.put(k, line.charAt(i) == '"' ? Tokenizer.removeSlashes(line.substring(i+1, line.length()-1)) : line.substring(i));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}

	public I18n(CharSequence data) {
		langMap = parseLang(data);
		at = langMap.getOrDefault("at", " 在 ");
	}

	public final String at;
	final Map<String, String> langMap;

	public String translate(String str) {
		String v = langMap.get(str);
		if (v != null) return v;

		// a:b:c
		int pos = str.indexOf(':');
		if (pos < 0) return str;

		v = langMap.get(str.substring(0, pos));
		if (v == null) return str;

		CharList sb = new CharList(v);
		CharList tmp = new CharList();

		int num = 0;
		int i, prevI = pos+1;
		while (true) {
			int kk = prevI;
			while (true) {
				i = str.indexOf(':', kk);

				int bracket = str.indexOf('\1', kk);
				if (bracket < 0 || bracket > i) break;

				kk = getBracketEnd(str, bracket);
			}

			tmp.clear();
			int j = sb.indexOf(tmp.append('%').append(++num));
			if (j < 0) {
				sb.append(str, prevI, str.length());
				break;
			}

			String seq = str.substring(prevI, i < 0 ? str.length() : i);
			CharSequence s = langMap.get(seq);
			if (s == null) {
				tmp.clear();
				inlineTranslate(seq, 0, tmp);
				s = tmp;
			}
			sb.replace("%"+num, s);

			if (i < 0) break;
			prevI = i+1;
		}
		return sb.toStringAndFree();
	}

	private void inlineTranslate(String str, int prevI, CharList out) {
		int i;
		while (true) {
			i = str.indexOf('\1', prevI);
			if (i < 0) break;
			int j = getBracketEnd(str, i);

			out.append(str, prevI, i);
			out.append(translate(str.substring(i+1, j)));

			prevI = j+1;
		}
		out.append(str, prevI, str.length());
	}

	private static int getBracketEnd(String str, int i) {
		int depth = 1;
		int j = i+1;
		for (;;) {
			char c = str.charAt(j);
			if (c == '\1') depth++;
			else if (c == '\0' && --depth == 0) break;
			if (++j == str.length()) throw new IllegalArgumentException("i18n错误 未闭合的括号: "+ str);
		}
		return j;
	}
}