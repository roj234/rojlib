package roj.text;

import roj.collect.BitSet;
import roj.compiler.plugins.annotations.Attach;
import roj.concurrent.LazyThreadLocal;
import roj.config.Tokenizer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;

/**
 * URI编解码工具
 * <ul>
 *   <li>URI标准化编码（RFC 2396） - {@link #encodeURI(CharSequence)}</li>
 *   <li>URI组件严格编码 - {@link #encodeURIComponent(CharSequence)}</li>
 *   <li>URI解码（支持+号转空格） - {@link #decodeURI(CharSequence)}</li>
 *   <li>文件路径安全转义 - {@link #escapeFilePath(CharSequence)}</li>
 *   <li>文件名称安全转义 - {@link #escapeFileName(CharSequence)}</li>
 * </ul>
 * encodeURI和component与JavaScript同名方法逻辑相同<br>
 * 文件名称转义需要decodeURI解码<p>
 *
 * 使用线程安全的{@link FastCharset}处理字符集，默认UTF-8编码
 *
 * @author Roj234
 * @since 2023/2/23 18:06
 */
public class URICoder {
	public static final LazyThreadLocal<FastCharset> CHARSET = new LazyThreadLocal<>(FastCharset.UTF8());

	public static final BitSet URI_SAFE = BitSet.from(TextUtil.digits).addAll("~!@$&*()_+-=/.,:;'");
	public static final BitSet URI_COMPONENT_SAFE = BitSet.from(TextUtil.digits).addAll("~!*()_-.'");

	public static String encodeURI(CharSequence src) { return encodeURI(new CharList(), src).toStringAndFree(); }
	public static String encodeURIComponent(CharSequence src) { return encodeURIComponent(new CharList(), src).toStringAndFree(); }
	@Attach("appendURI")
	public static <T extends Appendable> T encodeURI(T sb, CharSequence src) {
		ByteList bb = new ByteList();
		try {
			CHARSET.get().encodeFixedIn(src, bb);
			return pEncodeW(sb, bb, URI_SAFE);
		} finally {
			bb._free();
		}
	}
	@Attach("appendURIComponent")
	public static <T extends Appendable> T encodeURIComponent(T sb, CharSequence src) {
		ByteList bb = new ByteList();
		try {
			CHARSET.get().encodeFixedIn(src, bb);
			return pEncodeW(sb, bb, URI_COMPONENT_SAFE);
		} finally {
			bb._free();
		}
	}

	private static final BitSet FILE_NAME_INVALID = BitSet.from("%\\/:*?\"<>|"), FILE_PATH_INVALID = BitSet.from("%:*?\"<>|");

	public static String escapeFilePath(CharSequence src) { return pEncodeB(src, new CharList(), FILE_PATH_INVALID).toStringAndFree(); }
	public static String escapeFileName(CharSequence src) { return pEncodeB(src, new CharList(), FILE_NAME_INVALID).toStringAndFree(); }

	public static <T extends Appendable> T pEncodeW(T sb, DynByteBuf src, BitSet whitelist) {
		try {
			for (int i = 0; i < src.length(); i++) {
				char c = src.charAt(i);
				if (whitelist.contains(c)) sb.append(c);
				else sb.append("%").append(TextUtil.b2h(c>>>4)).append(TextUtil.b2h(c&15));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}
	private static CharList pEncodeB(CharSequence src, CharList sb, BitSet blacklist) {
		for (int i = 0; i < src.length(); i++) {
			char c = src.charAt(i);
			if (!blacklist.contains(c)) sb.append(c);
			else sb.append("%").append(TextUtil.b2h(c>>>4)).append(TextUtil.b2h(c&15));
		}
		return sb;
	}

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
}