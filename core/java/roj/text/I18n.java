package roj.text;

import roj.collect.HashMap;

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
		HashMap<String, String> map = new HashMap<>();
		if (content == null) return map;
		try {
			for (String line : LineReader.create(content)) {
				line = line.trim();
				if (line.startsWith("#") || line.isEmpty()) continue;

				int i = line.indexOf(": ");
				String k = line.substring(0, i++);
				i++;
				map.put(k, line.charAt(i) == '"' ? Tokenizer.unescape(line.substring(i+1, line.length()-1)) : line.substring(i));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}

	public I18n(CharSequence data) {langMap = parseLang(data);}
	public void add(CharSequence data) {langMap.putAll(parseLang(data));}
	public I18n(Map<String, String> langMap) {this.langMap = langMap;}

	final Map<String, String> langMap;

	public String translate(String str) {
		String v = langMap.get(str);
		if (v != null) return v;

		CharList out = new CharList();
		loopTranslate(str, out, 0, true);
		return out.toStringAndFree();
	}
	private int loopTranslate(String str, CharList out, int ptr, boolean top) {
		int nextStart;
		if (top) {
			nextStart = 0;
		} else {
			nextStart = str.indexOf('\1', ptr);
			if (nextStart < 0) {
				out.append(str, ptr, str.length());
				return str.length();
			}

			out.append(str, ptr, nextStart);
			ptr = nextStart+1;
		}

		int i = str.indexOf('\1', nextStart+1);
		if (i < 0 && top) {out.append(str);return str.length();}

		int nextEnd = top ? i : str.indexOf('\0', nextStart+1);
		if (i < nextEnd && i > nextStart) {
			ptr = loopTranslate(str, out, ptr, false);
			nextEnd = str.indexOf('\0', ptr);
		}
		if (nextEnd < 0) throw new IllegalStateException("i18nError "+str.replace('\1', '{').replace('\0', '}')+" 未闭合的括号，开始于"+nextStart);

		String content = str.substring(ptr, nextEnd);
		String translate = langMap.get(content);

		ptr = nextEnd;
		if (translate == null) {out.append(content);return ptr;}
		if (!translate.contains("%1")) {out.append(translate);return ptr;}

		var translateTmp = new CharList(translate);
		var tmp2 = new CharList();
		int num = 0;
		while (ptr < str.length()) {
			String key = "%" + ++num;
			int j = translateTmp.indexOf(key);
			if (j < 0) break;

			ptr = loopTranslate(str, tmp2, ptr, false);
			translateTmp.replace(key, tmp2);
			tmp2.clear();
		}
		tmp2._free();
		out.append(translateTmp);
		translateTmp._free();

		while (ptr < str.length()) {
			ptr = loopTranslate(str, out, ptr, false);
		}

		return ptr+1;
	}
}