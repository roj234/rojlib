package roj.text;

import org.jetbrains.annotations.Nullable;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;

import static java.lang.Character.*;

/**
 * @author Roj234
 * @since 2023/5/14 0014 22:03
 * FixedIn:  固定输入,适用已知长度
 * FixedOut: 固定输出,适用循环和缓冲的组合
 */
public abstract class UnsafeCharset {
	@Nullable
	public static UnsafeCharset getInstance(Charset charset) {
		if (StandardCharsets.UTF_8.equals(charset)) return UTF8.CODER;
		else if (UTF16n.is(charset)) return UTF16n.CODER;
		else if (GB18030.is(charset)) return GB18030.CODER;
		return null;
	}

	public abstract String name();

	public static final char INVALID = '�';
	static boolean isSurrogate(int c) {
		if (c < MIN_HIGH_SURROGATE || c > MAX_LOW_SURROGATE) return false;
		if (c >= MIN_LOW_SURROGATE) throw new IllegalArgumentException("unexpected low surrogate U+"+Integer.toHexString(c));
		return true;
	}
	static int codepoint(int h, int l) {
		if (l < MIN_LOW_SURROGATE || l > MAX_LOW_SURROGATE) throw new IllegalStateException("invalid surrogate pair "+h+","+l);
		return ((h << 10) + l) + (MIN_SUPPLEMENTARY_CODE_POINT - (MIN_HIGH_SURROGATE << 10) - MIN_LOW_SURROGATE);
	}

	public final void encodeFixedIn(CharSequence s, DynByteBuf out) { encodeFixedIn(s, 0, s.length(), out); }
	public final void encodeFixedIn(CharSequence s, int off, int end, DynByteBuf out) {
		int space = byteCount(s, off, end);
		if (!out.ensureWritable(space)) throw new IllegalArgumentException("没有足够的空间容纳编码后的"+space+"字节:"+out.writableBytes());

		int ow = out.wIndex();
		off = encodeLoop(s, off, end, out, space, 0);
		assert off == end : "off="+off+",end="+end;
		assert out.wIndex() == ow+space;
	}
	public final int encodePreAlloc(CharSequence s, DynByteBuf out, int byteCount) { return encodePreAlloc(s, 0, s.length(), out, byteCount); }
	/**
	 * @return out的可用空间
	 */
	public final int encodePreAlloc(CharSequence s, int off, int end, DynByteBuf out, int byteCount) {
		int ow = out.wIndex();
		off = encodeLoop(s, off, end, out, byteCount, 0);
		if (off != end) throw new IllegalArgumentException(!out.isWritable()?"缓冲区满":"预算的长度有误,并非"+byteCount);
		return (out.wIndex()-ow)-byteCount;
	}

	public final void encodeFully(CharSequence s, DynByteBuf out) { encodeFully(s, 0, s.length(), out); }
	public final void encodeFully(CharSequence s, int off, int end, DynByteBuf out) { encodeLoop(s, off, end, out, Integer.MAX_VALUE, 1024); }
	public final int encodeFixedOut(CharSequence s, int off, int end, DynByteBuf out) {
		return encodeLoop(s, off, end, out, out.writableBytes(), 0);
	}
	public final int encodeFixedOut(CharSequence s, int off, int end, DynByteBuf out, int outMax) {
		return encodeLoop(s, off, end, out, outMax, 0);
	}

	private static boolean alert;
	/**
	 * @param s CharList|CharBuffer|CharSlice|char[]|String|CharSequence
	 * @return off
	 */
	public final int encodeLoop(Object s, int off, int end, DynByteBuf out, int outMax, int minSpace) {
		if (!out.hasBuffer()) {
			ByteList bb = new ByteList();
			bb.ensureCapacity(1024);

			while (off < end) {
				bb.clear();
				off = encodeLoop(s,off,end,bb,bb.capacity(),0);
				out.put(bb);
			}

			bb._free();
			return off;
		}

		int off1 = 0;
		char[] arr;
		Class<?> k = s.getClass();
		found: {
			if (k == CharList.class) {
				CharList sb = (CharList) s;
				arr = sb.list;
				break found;
			} else if (s instanceof CharBuffer) {
				CharBuffer sb = (CharBuffer) s;
				if (sb.hasArray()) {
					arr = sb.array();
					off1 = sb.arrayOffset();
					off += off1;
					end += off1;
					break found;
				}
			} else if (k == CharList.Slice.class) {
				CharList.Slice sb = (CharList.Slice) s;
				arr = sb.list;
				off1 = sb.arrayOffset();
				off += off1;
				end += off1;
				break found;
			} else if (k == char[].class) {
				arr = (char[]) s;
				break found;
			}

			if (s.getClass() == String.class) {
				arr = ArrayCache.getCharArray(Math.min(end-off, 4096), false);
				String ss = s.toString();
				try {
					while (true) {
						int myLen = Math.min(end-off, arr.length);
						ss.getChars(off, off+myLen, arr, 0);

						int ow = out.wIndex();
						int encoded = encodeLoop(arr,0,myLen,out,outMax,minSpace);

						off += encoded;
						outMax -= out.wIndex()-ow;

						if (off == end || encoded < myLen) return off;
					}
				} finally {
					ArrayCache.putArray(arr);
				}
			}

			if (!alert) {
				alert = true;
				new Throwable("It is recommended to pre-copy your " + s.getClass().getName() + " to a CharBuffer or CharList").printStackTrace();
			}

			arr = ArrayCache.getCharArray(Math.min(end-off, 4096), false);
			CharSequence cs = (CharSequence) s;
			try {
				while (true) {
					int myLen = Math.min(end-off, arr.length);
					for (int i = 0; i < myLen; i++) arr[i] = cs.charAt(off+i);

					int ow = out.wIndex();
					int encoded = encodeLoop(arr,0,myLen,out,outMax,minSpace);

					off += encoded;
					outMax -= out.wIndex()-ow;

					if (off == end || encoded < myLen) return off;
				}
			} finally {
				ArrayCache.putArray(arr);
			}
		}

		while (true) {
			if (out.unsafeWritableBytes() < minSpace) {
				out.ensureWritable(out.unsafeWritableBytes() + minSpace);
				if (!out.isWritable()) throw new IllegalArgumentException("没有足够的空间:"+out.unsafeWritableBytes());
			}

			int uw = Math.min(outMax, out.unsafeWritableBytes());
			long x = unsafeEncode(arr, off, end, out.array(), out._unsafeAddr()+out.wIndex(), uw);

			off = (int) (x >>> 32);
			int delta = (uw-(int) x);
			outMax -= delta;
			out.wIndex(out.wIndex() + delta);

			if (off == end || outMax == 0 || delta == 0) break;
		}

		return off-off1;
	}

	public final void decodeFixedIn(DynByteBuf in, int len, Appendable out) {
		int end = in.rIndex + len;
		if (end > in.wIndex()) throw new IllegalArgumentException("没有"+len+"字节可供读取:"+in.readableBytes());
		decodeLoop(in,len,out,Integer.MAX_VALUE,false);
	}
	/**
	 * @return outMax
	 */
	public final int decodeFixedOut(DynByteBuf in, int len, Appendable out, int outMax) {
		int end = in.rIndex + len;
		if (end > in.wIndex()) throw new IllegalArgumentException("没有"+len+"字节可供读取:"+in.readableBytes());
		return decodeLoop(in,len,out,outMax,true);
	}
	/**
	 * @return outMax
	 */
	public final int decodeLoop(DynByteBuf in, int len, Appendable out, int outMax, boolean partial) {
		char[] arr;
		int off;
		int unsafeWritable;
		int kind;

		found: {
			if (out instanceof CharList) {
				CharList sb = (CharList) out;
				arr = sb.list;
				off = sb.length();
				unsafeWritable = arr.length - sb.length();
				kind = 0;
				break found;
			} else if (out instanceof CharBuffer) {
				CharBuffer sb = (CharBuffer) out;
				if (sb.hasArray()) {
					arr = sb.array();
					off = sb.arrayOffset() + sb.position();
					unsafeWritable = sb.remaining();
					kind = 1;
					if (unsafeWritable == 0 && outMax > 0) throw new IllegalArgumentException();
					break found;
				}
			}

			arr = ArrayCache.getCharArray(Math.min(outMax, 4096), false);
			off = 0;
			unsafeWritable = arr.length;
			kind = 2;
		}

		int rin = in.rIndex;
		if (rin < 0) throw new IllegalArgumentException("in.rIndex < 0");
		len += rin;
		try {
			while (true) {
				int uw = Math.min(outMax, unsafeWritable);
				long x = unsafeDecode(in.array(),in._unsafeAddr(),rin,len,arr,off,uw);

				rin = (int) (x >>> 32);
				int delta = (int) x - off;
				outMax -= delta;

				switch (kind) {
					case 0 -> {
						off += delta;
						((CharList) out).len = off;
						unsafeWritable -= delta;
					}
					case 1 -> {
						off += delta;
						((CharBuffer) out).position(off);
						unsafeWritable -= delta;
					}
					case 2 -> out.append(new CharList.Slice(arr, 0, delta));
				}

				if (rin == len || outMax == 0) break;

				if (kind == 0 && unsafeWritable < 1024) {
					CharList sb = (CharList) out;
					sb.ensureCapacity(arr.length+1024);
					arr = sb.list;
					unsafeWritable = arr.length - off;
				} else if (delta == 0) { // truncate
					if (unsafeWritable > 0 && !partial) throw new IllegalArgumentException("被截断");
					break;
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		} finally {
			in.rIndex = rin;
			if (kind == 2) ArrayCache.putArray(arr);
		}

		return outMax;
	}

	/**
	 * @return 高32位: 新off, 低32位: 新out_len
	 */
	public abstract long unsafeEncode(char[] in_ref, int in_off, int i_end, Object out_ref, long out_off, int out_len);
	/**
	 * @return 高32位: 新pos, 低32位: 新off
	 */
	public abstract long unsafeDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax);

	public static final int TRUNCATED = -1, MALFORMED = -2;
	public final void validate(DynByteBuf in, IntConsumer codepointAcceptor) {
		unsafeValidate(in.array(),in._unsafeAddr()+in.rIndex,in._unsafeAddr()+in.wIndex(),codepointAcceptor);
		in.rIndex = in.wIndex();
	}
	public abstract void unsafeValidate(Object ref, long base, long end, IntConsumer cs);

	public final int byteCount(CharSequence s) { return byteCount(s, 0, s.length()); }
	public abstract int byteCount(CharSequence s, int off, int len);
}