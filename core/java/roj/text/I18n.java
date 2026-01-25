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

				int i = line.indexOf('=');
				String k = line.substring(0, i++);
				map.put(k, Tokenizer.unescape(line.substring(i)));
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

		int i = str.indexOf(":[");
		if (i >= 0) {
			v = langMap.get(str.substring(0, i));
			if (v != null) {
				i += 2;
				CharList buf = new CharList(v);
				int idx = 0;

				char c;
				while (i < str.length() && (c = str.charAt(i)) != ']') {
					String key;
					if (c == '"') {
						int j = str.indexOf('"', ++i);
						key = str.substring(i, j);
						i = j + 1;
					} else {
						int j = str.indexOf(',', i);
						if (j < 0) j = str.length()-1;
						key = str.substring(i, j);
						key = langMap.getOrDefault(key, key);
						i = j + 1;
					}

					String placeholder = "%"+ ++ idx;
					int pos = buf.indexOf(placeholder);
					if (pos < 0) buf.append(key);
					else buf.replace(pos, pos + placeholder.length(), key);
				}

				return buf.toStringAndFree();
			}
		}
		return str;
	}
}