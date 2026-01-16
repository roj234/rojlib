package roj.text;

import roj.collect.BitSet;
import roj.compiler.plugins.annotations.Attach;
import roj.concurrent.LazyThreadLocal;
import roj.reflect.Unsafe;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;

import static roj.reflect.Unsafe.U;

/**
 * URI编解码工具
 * <ul>
 *   <li>URI标准化编码（RFC 2396） - {@link #encodeURI(CharSequence)}</li>
 *   <li>URI组件严格编码 - {@link #encodeURIComponent(CharSequence)}</li>
 *   <li>URI解码（支持+号转空格） - {@link #decodeURI(CharSequence)}</li>
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

	public static final BitSet URI_SAFE = BitSet.from(TextUtil.DIGITS).addAll("~!@$&*()_+-=/.,:;'");
	public static final BitSet URI_COMPONENT_SAFE = BitSet.from(TextUtil.DIGITS).addAll("~!*()_-.'");

	public static String encodeURI(CharSequence src) { return encodeURI(new CharList(), src).toStringAndFree(); }
	public static String encodeURIComponent(CharSequence src) { return encodeURIComponent(new CharList(), src).toStringAndFree(); }

	@Attach("appendURI")
	public static <T extends Appendable> T encodeURI(T sb, CharSequence src) {return encodeWhitelist(sb, src, URI_SAFE);}
	@Attach("appendURIComponent")
	public static <T extends Appendable> T encodeURIComponent(T sb, CharSequence src) {return encodeWhitelist(sb, src, URI_COMPONENT_SAFE);}

	public static <T extends Appendable> T encodeWhitelist(T sb, CharSequence src, BitSet whitelist) {
		ByteList bb = new ByteList();
		try {
			CHARSET.get().encodeFixedIn(src, bb);
			return pEncodeW(sb, bb, whitelist);
		} finally {
			bb.release();
		}
	}
	public static <T extends Appendable> T escapeBlacklist(T sb, CharSequence src, BitSet blacklist) {
		ByteList bb = new ByteList();
		try {
			CHARSET.get().encodeFixedIn(src, bb);

			for (int i = 0; i < bb.length(); i++) {
				char c = bb.charAt(i);
				if (c < 127 && !blacklist.contains(c)) sb.append(c);
				else sb.append("%").append(TextUtil.b2h(c>>>4)).append(TextUtil.b2h(c&15));
			}
		}  catch (IOException e) {
			Helpers.athrow(e);
		} finally {
			bb.release();
		}

		return sb;
	}

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

	public static String decodeURI(CharSequence src) throws MalformedURLException {return decodeURI(src, false);}
	public static String decodeURI(CharSequence src, boolean unescapePlus) throws MalformedURLException {
		CharList sb = new CharList();
		int i = decodeURI(sb.append(src), unescapePlus);
		String result = sb.substring(i);
		sb._free();
		return result;
	}

	/**
	 * 绝赞高性能之decodeURI
	 * @param sb 原地修改的缓冲区
	 * @param unescapePlus 是否解析+为空格
	 * @return 输出起始位置，往后到CharList结束均为解码出的内容
	 */
	public static int decodeURI(CharList sb, boolean unescapePlus) throws MalformedURLException {
		char[] chars = sb.list;
		int readPtr = 0;
		int readLimit = sb.len;
		int writePtr = readLimit;
		int writeLimit = chars.length;

		while (readPtr < readLimit) {
			char c = chars[readPtr];
			if (c == '%') {
				int shadowBytesStart = readPtr << 1;
				int shadowBytesEnd = shadowBytesStart;

				while (readPtr+2 < readLimit && chars[readPtr] == '%') {
					int high = TextUtil.h2b(chars[readPtr+1]);
					if (high < 0) break;

					int low = TextUtil.h2b(chars[readPtr+2]);
					if (low < 0) break;

					U.putByte(
							chars, Unsafe.ARRAY_CHAR_BASE_OFFSET + shadowBytesEnd,
							(byte) ((high << 4) | low)
					);
					shadowBytesEnd ++;

					readPtr += 3;
				}

				try {
					FastCharset fc = CHARSET.get();

					while (true) {
						int remaining = writeLimit - writePtr;
						// 这里不能是0，因为FC可能一次解码出好几(2)个char
						if (remaining < 32) {
							int safeReadPtr = shadowBytesStart >> 1;

							int newSize = writePtr + Math.max((readLimit - safeReadPtr) >> 1, 256) - safeReadPtr;

							// 如果清空readPtr能腾出至少256字节，就不再扩展了
							char[] newBuf = newSize > chars.length ? ArrayCache.getCharArray(newSize) : chars;
							System.arraycopy(chars, safeReadPtr, newBuf, 0, writePtr - safeReadPtr);

							if (chars != newBuf) ArrayCache.putArray(chars);
							chars = newBuf;
							writeLimit = newBuf.length;
							writePtr -= safeReadPtr;
							readLimit -= safeReadPtr;
							readPtr = safeReadPtr;
						}

						long x = fc.fastDecode(
								chars, Unsafe.ARRAY_CHAR_BASE_OFFSET, shadowBytesStart, shadowBytesEnd,
								chars, writePtr, remaining
						);

						shadowBytesStart = (int) (x >>> 32);

						int charsDecoded = (int) x - writePtr;
						writePtr += charsDecoded;

						if (shadowBytesStart == shadowBytesEnd) break;

						if (charsDecoded == 0) { // truncate
							throw new IllegalArgumentException("被截断");
						}
					}
				} catch (Exception e) {
					// not compatible with RFC 2396
					throw new MalformedURLException("无法解析UTF8:"+e.getMessage());
				}

				if (readPtr == readLimit) break;
				c = chars[readPtr];
			}
			if (c == '+' && unescapePlus) c = ' ';

			readPtr++;

			if (writePtr == writeLimit) {
				int newSize = writePtr + Math.max((readLimit - readPtr) >> 1, 256) - readPtr;

				char[] newBuf = newSize > chars.length ? ArrayCache.getCharArray(newSize) : chars;
				System.arraycopy(chars, readPtr, newBuf, 0, writePtr - readPtr);

				if (chars != newBuf) ArrayCache.putArray(chars);
				chars = newBuf;
				writeLimit = newBuf.length;
				writePtr -= readPtr;
				readLimit -= readPtr;
				readPtr = 0;
			}

			chars[writePtr++] = c;
		}

		sb.list = chars;
		sb.len = writePtr;
		return readLimit;
	}
}