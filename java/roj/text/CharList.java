package roj.text;

import org.jetbrains.annotations.NotNull;
import roj.collect.CharMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2021/6/19 1:28
 */
public class CharList implements CharSequence, Appendable {
	// region Number helper
	public static int getChars(long i, int radix, int charPos, char[] buf) {
		if (radix == 10) return getChars(i, charPos, buf);

		while (i >= radix) {
			buf[--charPos] = (char) TextUtil.digits[(int)(i % radix)];
			i /= radix;
		}
		buf[--charPos] = (char)TextUtil.digits[(int)i];
		return charPos;
	}
	public static int getChars(long l, int charPos, char[] buf) {
		long q;
		int r;

		// Get 2 digits/iteration using longs until quotient fits into an int
		while (l > Integer.MAX_VALUE) {
			q = l / 100;
			// really: r = i - (q * 100);
			r = (int)(l - ((q << 6) + (q << 5) + (q << 2)));
			l = q;
			buf[--charPos] = DigitOnes(r);
			buf[--charPos] = DigitTens(r);
		}

		return getChars((int) l, charPos, buf);
	}
	public static int getChars(int i, int charPos, char[] buf) {
		int r;

		int q;
		while (i >= 65536) {
			q = i / 100;
			// really: r = i2 - (q * 100);
			r = i - ((q << 6) + (q << 5) + (q << 2));
			i = q;
			buf[--charPos] = DigitOnes(r);
			buf[--charPos] = DigitTens(r);
		}

		// Fall thru to fast mode for smaller numbers
		// assert(i2 <= 65536, i2);
		do {
			q = (i * 52429) >>> (16 + 3);
			r = i - ((q << 3) + (q << 1));  // r = i2-(q2*10) ...
			buf[--charPos] = (char) (r+'0');
			i = q;
		} while (i != 0);

		return charPos;
	}
	private static char DigitTens(int r) {return (char) ((r/10) + '0');}
	private static char DigitOnes(int r) {return (char) ((r%10) + '0');}

	// endregion

	public char[] list;
	protected int len;

	public CharList() { list = ArrayCache.CHARS; }
	public CharList(int len) { list = ArrayCache.getCharArray(len, false); }
	public CharList(char[] array) {
		list = array;
		len = array.length;
	}

	public CharList(CharSequence s) {
		this.list = ArrayCache.getCharArray(s.length(), false);
		append(s);
	}

	public final char charAt(int i) {
		if (i >= len) throw new StringIndexOutOfBoundsException("off="+i+",len="+len);
		return list[i];
	}
	public final void set(int i, char c) {
		if (i >= len) throw new StringIndexOutOfBoundsException("off="+i+",len="+len);
		list[i] = c;
	}

	public final int length() { return len; }
	public final void setLength(int i) {
		if (i > list.length) ensureCapacity(i);
		this.len = i;
	}

	public final CharList readFully(Reader in) throws IOException { return readFully(in, true); }
	public final CharList readFully(Reader in, boolean close) throws IOException {
		ensureCapacity(len+127);

		int r;
		do {
			r = in.read(list, len, list.length-len);
			if (r <= 0) break;
			len += r;
			ensureCapacity(len+1);
		} while (true);
		if (close) in.close();
		return this;
	}

	public void ensureCapacity(int required) {
		if (required > list.length) {
			char[] newList = ArrayCache.getCharArray(Math.max(MathUtils.getMin2PowerOf(required), 256), false);
			if (len > 0) System.arraycopy(list, 0, newList, 0, Math.min(len, list.length));
			ArrayCache.putArray(list);
			list = newList;
		}
	}

	public void _secureFree() {
		for (int i = 0; i < len; i++) list[i] = 0;
		_free();
	}
	public void _free() {
		clear();

		char[] b = list;
		list = ArrayCache.CHARS;
		ArrayCache.putArray(b);
	}

	public String toStringAndFree() {
		String s = toString();
		_free();
		return s;
	}
	public final String toStringAndZero() {
		String s = toString();
		var arr1 = list;
		for (int i = 0; i < len; i++) arr1[i] = 0;
		return s;
	}

	public Appendable appendToAndFree(Appendable sb) {
		try {
			return sb.append(this);
		} catch (IOException e) {
			Helpers.athrow(e);
			return sb;
		} finally {
			_free();
		}
	}

	// region search
	public final boolean contains(CharSequence s) {return indexOf(s, 0) >= 0;}
	public final boolean containsAny(TrieTree<?> map) {return indexOf(map, 0) >= 0;}
	public final boolean containsAny(MyBitSet map) {return indexOf(map, 0) >= 0;}
	public final int indexOf(CharSequence s) { return indexOf(s, 0); }
	public final int indexOf(CharSequence s, int from) { return doMatch(s, from, len-s.length()+1); }
	public final boolean startsWith(CharSequence s) { return s.length() == 0 || doMatch(s, 0, 1) >= 0; }
	public final boolean endsWith(CharSequence s) { return s.length() == 0 || (len >= s.length() && doMatch(s, len-s.length(), len-s.length()+1) >= 0); }

	public final int indexOf(TrieTree<?> map, int pos) {
		var entry = new MyHashMap.Entry<>(new CInt(), null);
		while (pos < len) {
			map.match(this, pos, len, Helpers.cast(entry));
			int len = entry.getKey().value;
			if (len < 0) {
				pos++;
				continue;
			}

			return pos;
		}

		return -1;
	}
	public final int indexOf(MyBitSet map, int pos) {
		while (pos < len) {
			if (map.contains(list[pos])) return pos;
			pos++;
		}
		return -1;
	}

	public final int match(CharSequence s, int start, int end) {
		checkBounds(start, end, len);
		return doMatch(s, start, end);
	}
	private int doMatch(CharSequence s, int start, int end) {
		char[] b = list;
		char c = s.charAt(0);
		o:
		for (; start < end; start++) {
			if (b[start] == c) {
				for (int j = 1; j < s.length(); j++)
					if (b[start+j] != s.charAt(j))
						continue o;
				return start;
			}
		}

		return -1;
	}

	public final int lastIndexOf(CharSequence str) { return lastIndexOf(str, len); }
	public final int lastIndexOf(CharSequence str, int from) {
		int i = Math.min(len-str.length(), from);

		char[] b = list;
		o:
		for (; i >= 0; i--) {
			if (b[i] == str.charAt(0)) {
				for (int j = 1; j < str.length(); j++) {
					if (b[i + j] != str.charAt(j)) {
						continue o;
					}
				}
				return i;
			}
		}

		return -1;
	}

	public final boolean regionMatches(int i, CharSequence s) { return regionMatches(i,s,0,s.length()); }
	public final boolean regionMatches(int i, CharSequence s, int sOff) { return regionMatches(i,s,sOff,s.length()); }
	public boolean regionMatches(int index, CharSequence str, int off, int length) {
		if (index + length > len) return false;

		char[] list = this.list;
		for (int i = index; off < length; i++, off++) {
			if (list[i] != str.charAt(off)) return false;
		}

		return true;
	}

	// endregion
	// region append
	public final CharList append(char c) {
		ensureCapacity(len+1);
		list[len++] = c;
		return this;
	}
	public final CharList appendCodePoint(int cp) {
		if (!Character.isSupplementaryCodePoint(cp)) return append((char)cp);
		return append(Character.highSurrogate(cp)).append(Character.lowSurrogate(cp));
	}

	public final CharList append(char[] c) {
		return append(c, 0, c.length);
	}
	public CharList append(char[] c, int start, int end) {
		checkBounds(start,end,c.length);

		int l = end - start;
		if (l == 0) return this;

		ensureCapacity(len+l);
		System.arraycopy(c, start, list, len, l);
		len += l;
		return this;
	}
	public final CharList append(CharList c) {
		return append(c.list, 0, c.len);
	}
	public final CharList append(CharList c, int start, int end) {
		if (end > c.len) checkBounds(start,end,c.len);
		return append(c.list, start, end);
	}
	public final CharList append(CharSequence csq) {
		if (csq == null) return append("null");
		return append(csq, 0, csq.length());
	}
	public CharList append(CharSequence cs, int start, int end) {
		Class<?> c = cs.getClass();
		if (c == CharList.class) return append((CharList) cs, start, end);
		if (c == String.class) return append(cs.toString(), start, end);
		if (c == CharList.Slice.class) {
			Slice s = (Slice) cs;
			return append(s.list, s.off + start, s.off + end);
		}

		checkBounds(start,end,cs.length());
		ensureCapacity(len + end-start);

		char[] list = this.list;
		int j = len;
		for (int i = start; i < end; i++)
			list[j++] = cs.charAt(i);
		len = j;

		return this;
	}
	public final CharList append(String s) { if (s == null) s = "null"; return append(s,0,s.length()); }
	public CharList append(String s, int start, int end) {
		int len1 = end-start;
		ensureCapacity(len+len1);
		s.getChars(start, end, list, len);
		len += len1;
		return this;
	}
	public final CharList append(Object o) { return append(o == null ? "null" : o.toString()); }

	public final CharList append(boolean i) { return append(i?"true":"false"); }
	public final CharList append(int i) {
		if (i == Integer.MIN_VALUE) {
			append("-2147483648");
			return this;
		}

		int len = (i < 0) ? TextUtil.digitCount(-i)+1 : TextUtil.digitCount(i);
		ensureCapacity(this.len+len);
		int end = this.len+len;

		if (i < 0) {
			list[this.len] = '-';
			i = -i;
		}
		getChars(i, end, list);

		this.len = end;
		return this;
	}
	public final CharList append(long l) {
		if (l == Long.MIN_VALUE) {
			append("-9223372036854775808");
			return this;
		}

		int len = (l < 0) ? TextUtil.digitCount(-l)+1 : TextUtil.digitCount(l);
		ensureCapacity(this.len+len);
		int end = this.len+len;

		if (l < 0) {
			list[this.len] = '-';
			l = -l;
		}
		getChars(l, end, list);

		this.len = end;
		return this;
	}

	public final CharList append(float f) {
		StringBuilder sb = IOUtil.SharedBuf.get().numberHelper;
		sb.delete(0,sb.length()).append(f);
		ensureCapacity(len+sb.length());
		sb.getChars(0,sb.length(),list,len);
		len += sb.length();
		return this;
	}

	public final CharList append(double d) {
		StringBuilder sb = IOUtil.SharedBuf.get().numberHelper;
		sb.delete(0,sb.length()).append(d);
		ensureCapacity(len+sb.length());
		sb.getChars(0,sb.length(),list,len);
		len += sb.length();
		return this;
	}

	// endregion
	// region insert
	public final CharList padNumber(int val, int minLen) {
		int count = TextUtil.digitCount(val);
		if (count > minLen) return append(val);

		if (val < 0) {
			append('-');
			val = -val;
			count--;
		}

		return padEnd('0', minLen-count).append(val);
	}
	public final CharList padNumber(long val, int minLen) {
		int count = TextUtil.digitCount(val);
		if (count > minLen) return append(val);

		if (val < 0) {
			append('-');
			val = -val;
			count--;
		}

		return padEnd('0', minLen-count).append(val);
	}

	public final CharList padStart(char c, int count) { return pad(c, 0, count); }
	public final CharList padStart(CharSequence str, int count) { return pad(str, 0, count); }
	public final CharList padEnd(char c, int count) { return pad(c, len, count); }
	public final CharList padEnd(CharSequence str, int count) { return pad(str, len, count); }
	public CharList pad(char c, int off, int count) {
		if (count > 0) {
			ensureCapacity(len+count);
			System.arraycopy(list, off, list, off+count, len-off);
			len += count;
			count += off;
			while (off < count) list[off++] = c;
		}

		return this;
	}
	public CharList pad(CharSequence str, int off, int count) {
		if (str.length() < 1) throw new IllegalStateException("empty padding");
		if (count > 0) {
			ensureCapacity(len+count);
			System.arraycopy(list, off, list, off+count, len-off);
			len += count;
			count += off;
			while (true) {
				for (int j = 0; j < str.length(); j++) {
					list[off] = str.charAt(j);
					if (++off == count) return this;
				}
			}
		}

		return this;
	}

	public final CharList insert(int pos, char c) {
		ensureCapacity(len+1);
		if (len > pos) System.arraycopy(list, pos, list, pos+1, len-pos);
		list[pos] = c;
		len++;
		return this;
	}

	public final CharList insert(int pos, CharSequence s) { return insert(pos, s, 0, s.length()); }
	public final CharList insert(int pos, CharSequence s, int start, int end) {
		checkBounds(start,end,s.length());
		int len = end - start;
		if (len == 0) return this;
		ensureCapacity(len + this.len);

		char[] c = list;
		if (this.len - pos > 0)
			System.arraycopy(c, pos, c, pos + len, this.len - pos);

		while (start < end) c[pos++] = s.charAt(start++);
		this.len += len;

		return this;
	}
	// endregion
	// region delete
	public final void delete(int pos) { delete(pos, pos+1); }
	public final void delete(int start, int end) {
		checkBounds(start, end, len);
		int l = end-start;
		if (l > 0) {
			if (end != len) System.arraycopy(list, start+l, list, start, len-end);
			len -= l;
		}
	}

	public final CharList trim() {
		int len = this.len;
		int st = 0;
		char[] val = list;    /* avoid getfield opcode */

		while ((st < len) && (val[st] <= ' ')) {
			st++;
		}
		while ((st < len) && (val[len-1] <= ' ')) {
			len--;
		}

		this.len = len;
		if (st > 0) delete(0, st);
		return this;
	}

	public final CharList trimLast() {
		int len = this.len;
		char[] val = list;    /* avoid getfield opcode */

		while ((len > 0) && (val[len-1] <= ' ')) {
			len--;
		}

		this.len = len;
		return this;
	}

	public final void clear() { len = 0; }
	// endregion
	// region replace
	public final CharList replace(char a, char b) { return replace(a, b, 0, len); }
	public final CharList replace(char a, char b, int off, int len) {
		checkBounds(off,off+len,this.len);
		if (a == b) return this;
		char[] c = list;
		for (; off < len; off++)
			if (c[off] == a) c[off] = b;
		return this;
	}

	public final void replace(int start, int end, CharSequence s) {
		checkBounds(start,end,len);

		int delta = s.length() - (end - start);
		if (delta != 0) {
			if (delta > 0) ensureCapacity(len+delta);
			if (end < len) System.arraycopy(list, end, list, end + delta, len - end);
		}

		if (s.getClass() == String.class) {
			s.toString().getChars(0, s.length(), list, start);
		} else if (s.getClass() == CharList.class) {
			System.arraycopy(((CharList) s).list, 0, list, start, s.length());
		} else {
			char[] c = list;
			int j = 0;
			while (start < end)
				c[start++] = s.charAt(j++);
		}
		len += delta;
	}

	/**
	 * 不在替换结果中搜索
	 * "aaaa".replace("aa","a") => "aa"
	 */
	public final CharList replace(CharSequence str, CharSequence target) {
		CharList out = null;

		int prevI = 0, i = 0;
		while ((i = indexOf(str, i)) != -1) {
			if (prevI == 0) out = createReplaceOutputList();
			out.append(list, prevI, i).append(target);

			i += str.length();
			prevI = i;
		}

		if (prevI == 0) return this;
		out.append(list, prevI, len);

		ArrayCache.putArray(list);

		list = out.list;
		len = out.len;

		return this;
	}

	private CharList createReplaceOutputList() { return new CharList(Math.max(MathUtils.getMin2PowerOf(len) >> 1, 256)); }

	/**
	 * 在替换结果中搜索
	 * "aaaa".replaceInReplaceResult("aa","a") => "a"
	 */
	public final CharList replaceInReplaceResult(CharSequence str, CharSequence target) {
		int pos = len;
		while ((pos = lastIndexOf(str, pos)) != -1) {
			replace(pos, pos+str.length(), target);
		}
		return this;
	}
	public final CharList replaceBatch(String[] str, String[] target) {
		TrieTree<String> map = new TrieTree<>();
		for (int i = 0; i < str.length; i++) map.put(str[i], target[i]);
		return replaceBatch(map);
	}
	public final CharList replaceBatch(TrieTree<String> map) {
		CharList out = null;
		int prevI = 0;

		int pos = 0;

		MyHashMap.Entry<CInt, String> entry = new MyHashMap.Entry<>(new CInt(), null);
		while (pos < len) {
			map.match(this, pos, len, entry);
			int len = entry.getKey().value;
			if (len < 0) {
				pos++;
				continue;
			}

			if (prevI == 0) out = createReplaceOutputList();
			out.append(list, prevI, pos).append(entry.getValue());

			pos += len;
			prevI = pos;
		}

		if (prevI == 0) return this;
		out.append(list, prevI, len);

		ArrayCache.putArray(list);

		list = out.list;
		len = out.len;

		return this;
	}
	public final CharList replaceBatch(CharMap<String> map) {
		CharList out = null;
		int prevI = 0;
		int pos = 0;

		while (pos < len) {
			String rpl = map.get(list[pos]);
			if (rpl == null) {
				pos++;
				continue;
			}

			if (prevI == 0) out = createReplaceOutputList();
			out.append(list, prevI, pos).append(rpl);

			pos++;
			prevI = pos;
		}

		if (prevI == 0) return this;
		out.append(list, prevI, len);

		ArrayCache.putArray(list);

		list = out.list;
		len = out.len;

		return this;
	}
	public final int preg_replace(Pattern regexp, CharSequence literal) {
		CharList out = null;

		Matcher m = regexp.matcher(this);

		int count = 0;
		int i = 0;
		while (m.find(i)) {
			if (i == 0) out = createReplaceOutputList();
			out.append(list, i, m.start()).append(literal);

			i = m.end();
			count++;
		}

		if (i == 0) return 0;
		out.append(list, i, len);

		ArrayCache.putArray(list);

		list = out.list;
		len = out.len;
		return count;
	}
	public final int preg_replace_callback(Pattern regexp, Function<Matcher,CharSequence> callback) {
		CharList out = null;

		Matcher m = regexp.matcher(this);

		int count = 0;
		int i = 0;
		while (m.find(i)) {
			if (i == 0) out = createReplaceOutputList();
			out.append(list, i, m.start()).append(callback.apply(m));

			i = m.end();
			count++;
		}

		if (i == 0) return 0;
		out.append(list, i, len);

		ArrayCache.putArray(list);

		list = out.list;
		len = out.len;
		return count;
	}
	public final int preg_match_callback(Pattern regexp, Consumer<Matcher> callback) {
		Matcher m = regexp.matcher(this);
		int count = 0;
		int i = 0;
		while (m.find(i)) {
			callback.accept(m);

			i = m.end();
			count++;
		}
		return count;
	}
	// endregion

	public CharList reverse() {
		var val = list;
		int length = len - 1;
		for (int i = (length - 1) >> 1; i >= 0; i--) {
			int j = length - i;
			var ch = val[i];
			val[i] = val[j];
			val[j] = ch;
		}
		return this;
	}

	public final CharSequence subSequence(int start, int end) {
		checkBounds(start,end,len);
		if (start == 0 && end == len) return this;
		return start==end ? "" : new Slice(list, start, end);
	}
	public final String toString() {return substring(0, len);}
	public String substring(int start) {return substring(start, len);}
	public final String substring(int start, int end) {
		checkBounds(start,end,len);
		return start==end ? "" : new String(list, start, end-start);
	}

	public final char[] toCharArray() { return Arrays.copyOf(list, len); }
	public final CharBuffer toCharBuffer() { return CharBuffer.wrap(list, 0, len); }

	private static void checkBounds(int start, int end, int len) {
		if (start < 0 || end > len || len < end - start || start > end)
			throw new StringIndexOutOfBoundsException("start="+ start +",end="+ end +",length="+len);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CharSequence cs)) return false;
		if (len != cs.length()) return false;

		if (cs instanceof CharList anotherList && anotherList.getClass() == CharList.class) {
			return Arrays.equals(list, 0, len, anotherList.list, 0, len);
		}

		char[] c = list;
		for (int i = 0; i < len; i++) {
			if (c[i] != cs.charAt(i)) return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 0;

		char[] c = list;
		for (int i = 0; i < len; i++) {
			hash = 31 * hash + c[i];
		}
		return hash;
	}

	public static final class Slice implements CharSequence {
		public final char[] list;
		private final int off, len;

		public Slice(char[] list, int start, int end) {
			this.list = list;
			this.off = start;
			this.len = end-start;
		}

		public int arrayOffset() { return off; }
		public int length() { return len; }
		public char charAt(int i) {
			if (i >= len) throw new StringIndexOutOfBoundsException("off="+i+",len="+len);
			return list[off+i];
		}
		public CharSequence subSequence(int start, int end) {
			if (start == 0 && end == len) return this;
			checkBounds(start,end,len);

			return start==end ? "" : new Slice(list, start+off, end+off);
		}
		@NotNull
		public String toString() { return new String(list, off, len); }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof CharSequence)) return false;

			CharSequence cs = (CharSequence) o;

			if (len != cs.length()) return false;

			int len = this.len+off;
			int j = 0;
			for (int i = off; i < len; i++) {
				if (list[i] != cs.charAt(j++)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int hashCode() {
			int hash = 0;

			int len = this.len+off;
			for (int i = off; i < len; i++) {
				hash = 31 * hash + list[i];
			}
			return hash;
		}
	}

	public int compareTo(CharSequence o) {
		int len1 = len;
		int len2 = o.length();

		char[] v1 = list;
		char[] v2;
		int lim = Math.min(len1, len2);
		if (o instanceof CharList) {
			v2 = ((CharList) o).list;

			int i = 0;
			while (i < lim) {
				int c1 = v1[i] - v2[i];
				if (c1 != 0) return c1;
				i++;
			}
		} else {
			String s = o.toString();
			v2 = ArrayCache.getCharArray(512, false);
			int off = 0;
			while (off < lim) {
				int end = Math.min(off+2, lim);
				s.getChars(off,end,v2,0);

				int i = 0;
				while (off < end) {
					int c1 = v1[off++] - v2[i];
					if (c1 != 0) return c1;
					i++;
				}
			}
			ArrayCache.putArray(v2);
		}
		return len1 - len2;
	}
}