package roj.net;

import roj.collect.MyBitSet;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;

/**
 * @author Roj234
 * @since 2023/2/23 0023 18:06
 */
public class URIUtil {
	public static String decodeURI(CharSequence src) throws MalformedURLException {
		return decodeURI(src, IOUtil.getSharedCharBuf(), IOUtil.getSharedByteBuf()).toString();
	}
	public static CharList decodeURI(CharSequence src, CharList sb, ByteList tmp) throws MalformedURLException {
		tmp.clear();

		int len = src.length();
		int i = 0;

		while (i < len) {
			char c = src.charAt(i);
			switch (c) {
				case '+': c = ' '; break;
				case '%':
					try {
						while (true) {
							if (i+3 > len) break;
							if (src.charAt(i) != '%') break;

							if (src.charAt(i+1) == 'u') {
								if (tmp.wIndex() > 0) {
									ByteList.decodeUTF(tmp.wIndex(), sb, tmp);
									tmp.clear();
								}

								if (i+6 > len) break;
								try {
									sb.append((char) TextUtil.parseInt(src, i+2, i+6, 16));
								} catch (Exception e) {
									break;
								}
								i += 6;
							} else {
								try {
									tmp.put((byte) TextUtil.parseInt(src, i+1, i+3, 16));
								} catch (Exception e) {
									break;
								}
								i += 3;
							}
						}

						if (tmp.wIndex() > 0) {
							ByteList.decodeUTF(tmp.wIndex(), sb, tmp);
							tmp.clear();
						}

						if (i >= len) return sb;
						c = src.charAt(i);
					} catch (IOException e) {
						// not compatible with RFC 2396
						throw new MalformedURLException("无法作为UTF8解析:" + e.getMessage());
					}
					break;
			}

			sb.append(c);
			i++;
		}
		return sb;
	}

	public static final MyBitSet URI_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!@#$&*()_+-=/?.,:;'");
	public static final MyBitSet URI_COMPONENT_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!*()_-.'");

	public static CharList encodeURI(CharSequence src) {
		return encodeURI(src, IOUtil.getSharedByteBuf(), IOUtil.getSharedCharBuf(), URI_SAFE);
	}
	public static CharList encodeURIComponent(CharSequence src) {
		return encodeURI(src, IOUtil.getSharedByteBuf(), IOUtil.getSharedCharBuf(), URI_COMPONENT_SAFE);
	}
	public static <T extends Appendable> T encodeURI(CharSequence src, ByteList tmpib, T ob, MyBitSet safe) {
		tmpib.clear(); tmpib.putUTFData(src);

		try {
			for (int i = 0; i < tmpib.wIndex(); i++) {
				int j = tmpib.list[i]&0xFF;
				if (safe.contains(j)) ob.append((char) j);
				else ob.append('%').append(Integer.toString(j, 16));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return ob;
	}
}
