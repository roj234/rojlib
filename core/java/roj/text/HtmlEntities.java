package roj.text;

import roj.collect.HashMap;
import roj.collect.TrieTree;
import roj.compiler.plugins.annotations.Attach;
import roj.config.node.IntValue;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2025/05/30 19:40
 */
public class HtmlEntities {
	public static CharSequence escapeHtml(CharSequence str) {
		var out = h(str);
		return out == null ? str : out.toStringAndFree();
	}
	@Attach("appendHtmlEntities")
	public static CharList escapeHtml(CharList to, CharSequence str) {
		var out = h(str);
		if (out == null) to.append(str);
		else out.appendToAndFree(to);
		return to;
	}
	@Attach("htmlEntities")
	public static CharList escapeHtmlInline(CharList sb) {
		return sb.replaceBatch(EncodeMap);
	}

	public static CharSequence unescapeHtml(CharSequence str) {
		var out = hd(str);
		return out == null ? str : out.toStringAndFree();
	}
	@Attach("appendDeHtmlEntities")
	public static void unescapeHtml(CharSequence str, CharList to) {
		CharList out = hd(str);
		if (out == null) to.append(str);
		else out.appendToAndFree(to);
	}
	@Attach("deHtmlEntities")
	public static CharList unescapeHtmlInline(CharList sb) {
		var out = hd(sb);
		if (out != null) {
			ArrayCache.putArray(sb.list);
			sb.list = out.list;
			sb.len = out.len;
		}
		return sb;
	}

	private static CharList h(CharSequence s) { return replaceMulti(s, null, EncodeMap); }
	private static CharList hd(CharSequence in) { return replaceMulti(in, AmpBang.matcher(in), Tab.DecodeMap); }

	private static CharList replaceMulti(CharSequence in, Matcher m, TrieTree<String> tree) {
		int len = in.length();
		CharList out = null;
		int prevI = 0, i = 0;

		HashMap.Entry<IntValue, String> entry = new HashMap.Entry<>(new IntValue(), null);
		while (i < len) {
			tree.match(in, i, len, entry);
			int matchLen = entry.getKey().value;
			if (matchLen < 0) {
				if (m == null || !m.find(i)) {
					m = null;
					i++;
				} else {
					if (out == null) out = new CharList(len);

					out.append(in, prevI, m.start(0));
					out.appendCodePoint(Integer.parseInt(m.group(1)));
					i = prevI = m.end(0);
				}
			} else {
				if (out == null) out = new CharList(len);

				out.append(in, prevI, i).append(entry.getValue());

				i += matchLen;
				prevI = i;
			}
		}

		return out == null ? null : out.append(in, prevI, len);
	}

	private static final Pattern AmpBang = Pattern.compile("&#([0-9]{1,8});");
	public static final TrieTree<String> EncodeMap = new TrieTree<>();
	static {
		EncodeMap.put("&", "&amp;");
		EncodeMap.put("<", "&lt;");
		EncodeMap.put(">", "&gt;");
		EncodeMap.put("\"", "&quot;");
		EncodeMap.put("'", "&apos;");
	}
	private static final class Tab {
		static final TrieTree<String> DecodeMap = new TrieTree<>();
		static {
			// Reference: https://www.degraeve.com/reference/specialcharacters.php
			// Reference: https://box3.cn
			// Warning: &nbsp did not reference ' '
			String TABLE = null;
			try {
				TABLE = IOUtil.getTextResourceIL("roj/text/HtmlEntities.txt");
			} catch (IOException e) {
				Helpers.athrow(e);
			}

			int i = 0;
			int l;
			int[] s = new int[4];
			while (i < TABLE.length()) {
				int k = TABLE.indexOf('\n', i);
				if (k < 0) k = TABLE.length();
				l = 0;

				do {
					int j = TABLE.indexOf(';', i)+1;
					if (j > k || j == 0) break;

					s[l++] = i;
					s[l++] = j;
					i = j;
				} while (TABLE.charAt(i) == '&');

				String val = TABLE.substring(i,k);
				i = k+1;

				DecodeMap.put(TABLE, s[0], s[1], val);
				if (l > 2) DecodeMap.put(TABLE, s[2], s[3], val);
			}
		}
	}
}
