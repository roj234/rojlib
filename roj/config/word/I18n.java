package roj.config.word;

import roj.text.CharList;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Localization Util
 *
 * @author solo6975
 * @since 2021/6/17 23:43
 */
public class I18n {
	public static final I18n NULL = new I18n("");

	public I18n(CharSequence data) {
		translateMap = TextUtil.parseLang(data);
		at = translateMap.getOrDefault("at", " 在 ");
	}

	public final String at;
	final Map<String, String> translateMap;

	public String translate(String s) {
		String v = translateMap.get(s);
		if (v != null) return v;
		// a:b:c
		CharList cl = new CharList();
		List<String> tmp = TextUtil.split(new ArrayList<>(), s, ':');
		if (tmp.size() <= 1) return s;
		cl.clear();

		v = translateMap.get(tmp.get(0));
		if (v == null) return s;

		cl.append(v);
		CharList cl2 = new CharList(4);
		for (int i = 1; i < tmp.size(); i++) {
			cl.replace(cl2.append('%').append(Integer.toString(i)), translate(tmp.get(i)));
			cl2.clear();
		}

		return cl.toString();
	}
}
