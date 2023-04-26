package roj.net;

import roj.collect.MyBitSet;
import roj.io.IOUtil;
import roj.reflect.FieldAccessor;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;

/**
 * @author Roj234
 * @since 2023/2/23 0023 18:06
 */
public class URIUtil {
	public static String decodeURI(CharSequence src) throws MalformedURLException {
		CharList sb = IOUtil.ddLayeredCharBuf();
		ByteList bb = IOUtil.ddLayeredByteBuf();

		try {
			return decodeURI(src, sb, bb).toString();
		} finally {
			sb._free();
			bb._free();
		}
	}
	public static <T extends Appendable> T decodeURI(CharSequence src, T sb, DynByteBuf tmp) throws MalformedURLException {
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
					} catch (Exception e) {
						// not compatible with RFC 2396
						throw new MalformedURLException("无法作为UTF8解析:" + e.getMessage());
					}
					break;
			}

			try {
				sb.append(c);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
			i++;
		}
		return sb;
	}

	public static final MyBitSet URI_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!@#$&*()_+-=/?.,:;'");
	public static final MyBitSet URI_COMPONENT_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!*()_-.'");

	public static String encodeURI(CharSequence src) {
		return encodeURI(IOUtil.ddLayeredCharBuf(), src).toStringAndFree();
	}
	public static String encodeURIComponent(CharSequence src) {
		return encodeURIComponent(IOUtil.ddLayeredCharBuf(), src).toStringAndFree();
	}
	public static <T extends Appendable> T encodeURI(T sb, CharSequence src) {
		ByteList bb = IOUtil.ddLayeredByteBuf();
		try {
			return encodeURI(bb.putUTFData(src), sb, URI_SAFE);
		} finally {
			bb._free();
		}
	}
	public static <T extends Appendable> T encodeURIComponent(T sb, CharSequence src) {
		ByteList bb = IOUtil.ddLayeredByteBuf();
		try {
			return encodeURI(bb.putUTFData(src), sb, URI_COMPONENT_SAFE);
		} finally {
			bb._free();
		}
	}
	public static <T extends Appendable> T encodeURI(DynByteBuf ib, T ob, MyBitSet safe) {
		try {
			Object ref = ib.array();
			long off = ib._unsafeAddr();
			long end = off + ib.readableBytes();

			while (off < end) {
				int c = FieldAccessor.u.getByte(ref, off++)&0xFF;
				ib.rIndex++;

				if (safe.contains(c)) ob.append((char) c);
				else ob.append('%').append(Integer.toString(c, 16));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return ob;
	}
}
