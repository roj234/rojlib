package roj.text;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.compiler.plugins.annotations.Attach;
import roj.concurrent.LazyThreadLocal;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/2/23 0023 18:06
 */
public class Escape {
	public static final LazyThreadLocal<FastCharset> CHARSET = new LazyThreadLocal<>(FastCharset.UTF8());

	public static String decodeURI(CharSequence src) throws MalformedURLException {
		ByteList bb = new ByteList();
		try {
			return decodeURI(new CharList(), bb, src, false).toStringAndFree();
		} finally {
			bb._free();
		}
	}
	@SuppressWarnings("fallthrough")
	public static <T extends Appendable> T decodeURI(T sb, DynByteBuf tmp, CharSequence src, boolean unescapePlus) throws MalformedURLException {
		tmp.clear();

		int len = src.length();
		int i = 0;

		while (i < len) {
			char c = src.charAt(i);
			if (c == '%') {
				while (i+2 < len && src.charAt(i) == '%') {
					try {
						tmp.put((byte)Tokenizer.parseNumber(src, i+1, i+3, 1));
					} catch (NumberFormatException e) {break;}
					i += 3;
				}

				try {
					CHARSET.get().decodeFixedIn(tmp, tmp.wIndex(), sb);
				} catch (Exception e) {
					// not compatible with RFC 2396
					throw new MalformedURLException("无法解析UTF8:"+e.getMessage());
				}

				tmp.clear();
				if (i == len) break;
				c = src.charAt(i);
			}
			if (c == '+' && unescapePlus) c = ' ';

			try {
				sb.append(c);
			} catch (IOException e) {Helpers.athrow(e);}
			i++;
		}
		return sb;
	}

	public static final MyBitSet URI_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!$&*()_+-.,:;'");
	public static final MyBitSet URI_COMPONENT_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!*()_-.'");

	public static String encodeURI(CharSequence src) { return encodeURI(new CharList(), src).toStringAndFree(); }
	public static String encodeURIComponent(CharSequence src) { return encodeURIComponent(new CharList(), src).toStringAndFree(); }
	@Attach("appendURI")
	public static <T extends Appendable> T encodeURI(T sb, CharSequence src) {
		ByteList bb = new ByteList();
		try {
			CHARSET.get().encodeFixedIn(src, bb);
			return escape(sb, bb, URI_SAFE);
		} finally {
			bb._free();
		}
	}
	@Attach("appendURIComponent")
	public static <T extends Appendable> T encodeURIComponent(T sb, CharSequence src) {
		ByteList bb = new ByteList();
		try {
			CHARSET.get().encodeFixedIn(src, bb);
			return escape(sb, bb, URI_COMPONENT_SAFE);
		} finally {
			bb._free();
		}
	}
	public static <T extends Appendable> T escape(T sb, DynByteBuf src, MyBitSet safe) {
		try {
			for (int i = 0; i < src.length(); i++) {
				char c = src.charAt(i);
				if (safe.contains(c)) sb.append(c);
				else sb.append("%").append(TextUtil.b2h(c>>>4)).append(TextUtil.b2h(c&15));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}

	private static final MyBitSet FILE_NAME_INVALID = MyBitSet.from("\\/:*?\"<>|"), FILE_PATH_INVALID = MyBitSet.from(":*?\"<>|");

	public static String escapeFilePath(CharSequence src) { return escapeb(src, new CharList(), FILE_PATH_INVALID).toStringAndFree(); }
	public static String escapeFileName(CharSequence src) { return escapeb(src, new CharList(), FILE_NAME_INVALID).toStringAndFree(); }
	private static CharList escapeb(CharSequence src, CharList sb, MyBitSet blacklist) {
		for (int i = 0; i < src.length(); i++) {
			char c = src.charAt(i);
			if (!blacklist.contains(c)) sb.append(c);
			else sb.append("%").append(TextUtil.b2h(c>>>4)).append(TextUtil.b2h(c&15));
		}
		return sb;
	}

	public static CharSequence htmlEntities(CharSequence str) {
		var out = h(str);
		return out == null ? str : out.toStringAndFree();
	}
	@Attach("appendHtmlEntities")
	public static CharList htmlEntities_Append(CharList to, CharSequence str) {
		var out = h(str);
		if (out == null) to.append(str);
		else out.appendToAndFree(to);
		return to;
	}
	@Attach("htmlEntities")
	public static CharList htmlEntities_Inline(CharList sb) {
		var out = h(sb);
		if (out != null) {
			ArrayCache.putArray(sb.list);
			sb.list = out.list;
			sb.len = out.len;
		}
		return sb;
	}

	public static CharSequence deHtmlEntities(CharSequence str) {
		var out = hd(str);
		return out == null ? str : out.toStringAndFree();
	}
	@Attach("appendDeHtmlEntities")
	public static void deHtmlEntities_Append(CharList to, CharSequence str) {
		CharList out = hd(str);
		if (out == null) to.append(str);
		else out.appendToAndFree(to);
	}
	@Attach("deHtmlEntities")
	public static CharList deHtmlEntities_Inline(CharList sb) {
		var out = hd(sb);
		if (out != null) {
			ArrayCache.putArray(sb.list);
			sb.list = out.list;
			sb.len = out.len;
		}
		return sb;
	}

	private static CharList h(CharSequence s) { return replaceMulti(s, null, HtmlEncode); }
	private static CharList hd(CharSequence in) { return replaceMulti(in, AmpBang.matcher(in), HtmlTable.HtmlDecode); }

	private static CharList replaceMulti(CharSequence in, Matcher m, TrieTree<String> tree) {
		int len = in.length();
		CharList out = null;
		int prevI = 0, i = 0;

		MyHashMap.Entry<CInt, String> entry = new MyHashMap.Entry<>(new CInt(), null);
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
	public static final TrieTree<String> HtmlEncode = new TrieTree<>();
	static {
		HtmlEncode.put("&", "&amp;");
		HtmlEncode.put("<", "&lt;");
		HtmlEncode.put(">", "&gt;");
		HtmlEncode.put("\"", "&quot;");
		HtmlEncode.put("'", "&apos;");
	}
	private static final class HtmlTable {
		static final TrieTree<String> HtmlDecode = new TrieTree<>();
		static {
			// Reference: https://www.degraeve.com/reference/specialcharacters.php
			// Reference: https://box3.cn
			// Warning: &nbsp did not reference ' '
			String TABLE = null;
			try {
				TABLE = IOUtil.getTextResourceIL("roj/text/HtmlEscape.txt");
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

				HtmlDecode.put(TABLE, s[0], s[1], val);
				if (l > 2) HtmlDecode.put(TABLE, s[2], s[3], val);
			}
		}
	}
}