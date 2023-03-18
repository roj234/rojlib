package roj.text;

import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.math.MutableInt;
import roj.util.ArrayCache;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2021/6/19 1:28
 */
public class CharList implements CharSequence, Appender {
	// region Number helper
	static void getChars(long l, int charPos, char[] buf) {
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

		getChars((int) l, charPos, buf);
	}
	static void getChars(int i, int charPos, char[] buf) {
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
	}
	private static char DigitTens(int r) {
		return (char) ((r/10) + '0');
	}
	private static char DigitOnes(int r) {
		return (char) ((r%10) + '0');
	}

	// endregion

	public char[] list;
	protected int len;

	public CharList() { list = ArrayCache.CHARS; }
	public CharList(int len) { list = new char[len]; }
	public CharList(char[] array) {
		list = array;
		len = array.length;
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
			int newLen = Math.max(((required * 3) >> 1), 32);

			ArrayCache cache = ArrayCache.getDefaultCache();
			cache.putArray(list);
			char[] newList = cache.getCharArray(newLen, false);

			if (len > 0) System.arraycopy(list, 0, newList, 0, Math.min(len, list.length));
			list = newList;
		}
	}

	// region search
	public final boolean contains(CharSequence s) { return indexOf(s, 0) >= 0; }
	public final int indexOf(CharSequence s) { return indexOf(s, 0); }
	public final int indexOf(CharSequence s, int from) { return match(s, from, len-s.length()+1); }
	public final boolean startsWith(CharSequence s) { return s.length() == 0 || match(s, 0, 1) >= 0; }
	public final boolean endsWith(CharSequence s) { return s.length() == 0 || match(s, len-s.length(), len-s.length()+1) >= 0; }

	private int match(CharSequence s, int start, int end) {
		char[] b = list;
		o:
		for (; start < end; start++) {
			if (b[start] == s.charAt(0)) {
				for (int j = 1; j < s.length(); j++) {
					if (b[start + j] != s.charAt(j)) {
						continue o;
					}
				}
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
		if (end > c.len) throw new StringIndexOutOfBoundsException("len="+c.len +",start="+start+",end="+end);
		return append(c.list, start, end);
	}
	public final CharList append(CharSequence csq) {
		if (csq == null) return append("null");
		return append(csq, 0, csq.length());
	}
	public CharList append(CharSequence cs, int start, int end) {
		if (cs instanceof CharList) return append((CharList) cs, start, end);
		if (cs instanceof Slice) {
			Slice s = (Slice) cs;
			return append(s.array, s.off + start, s.off + end);
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
	public final CharList append(String s) {
		if (s == null) s = "null";
		ensureCapacity(len + s.length());
		s.getChars(0, s.length(), list, len);
		len += s.length();
		return this;
	}
	public final CharList append(Object o) {
		return append(o == null ? "null" : o.toString());
	}

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
		StringBuilder sb = IOUtil.SharedCoder.get().numberHelper;
		sb.delete(0,sb.length()).append(f);
		ensureCapacity(len+sb.length());
		sb.getChars(0,sb.length(),list,len);
		len += sb.length();
		return this;
	}

	public final CharList append(double d) {
		StringBuilder sb = IOUtil.SharedCoder.get().numberHelper;
		sb.delete(0,sb.length()).append(d);
		ensureCapacity(len+sb.length());
		sb.getChars(0,sb.length(),list,len);
		len += sb.length();
		return this;
	}

	// endregion
	// region insert
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

	public final void clear() { len = 0; }
	// endregion
	// region replace
	public final CharList replace(char a, char b) { return replace(a, b, 0, len); }
	public final CharList replace(char a, char b, int off, int len) {
		checkBounds(off,off+len,this.len);
		char[] c = list;
		for (; off < len; off++)
			if (c[off] == a) c[off] = b;
		return this;
	}

	public final void replace(int start, int end, CharSequence s) {
		checkBounds(start,end,len);

		int l = end - start;
		if (l > 0) {
			if (l > s.length()) {
				int delta = s.length() - l; // < 0
				System.arraycopy(list, end, list, end + delta, this.len - end);
				this.len += delta;
				end = start + s.length();
			} else if (l < s.length()) {
				insert(end, s, l, s.length());
			}

			char[] c = list;
			int j = 0;
			while (start < end)
				c[start++] = s.charAt(j++);
		}
	}

	/**
	 * 不在替换结果中搜索
	 * "aaaa".replace("aa","a") => "aa"
	 */
	public final CharList replace(CharSequence str, CharSequence target) {
		CharList out = null;

		String targetArray = target.toString();

		int prevI = 0, i = 0;
		while ((i = indexOf(str, i)) != -1) {
			if (prevI == 0) out = new CharList(len);
			out.append(list, prevI, i).append(targetArray);

			i += str.length();
			prevI = i;
		}

		if (prevI == 0) return this;
		out.append(list, prevI, len);

		ArrayCache.getDefaultCache().putArray(list);

		list = out.list;
		len = out.len;

		return this;
	}
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
	public final CharList replaceMulti(String[] str, String[] target) {
		TrieTree<String> map = new TrieTree<>();
		for (int i = 0; i < str.length; i++) map.put(str[i], target[i]);
		return replaceMulti(map);
	}
	public final CharList replaceMulti(TrieTree<String> map) {
		int pos = 0;

		MyHashMap.Entry<MutableInt, String> entry = new MyHashMap.Entry<>(new MutableInt(), null);
		while (pos < len) {
			map.longestIn(this, pos, len, entry);
			int len = entry.getKey().getValue();
			if (len < 0) {
				pos++;
				continue;
			}

			replace(pos, pos+len, entry.getValue());
			pos += entry.getValue().length();
		}

		return this;
	}
	public final void replaceEx(Pattern regexp, CharSequence literal) {
		CharList out = null;

		Matcher m = regexp.matcher(this);

		int i = 0;
		while (m.find(i)) {
			if (i == 0) out = new CharList(this.len);
			out.append(list, i, m.start()).append(literal);

			i = m.end();
		}

		if (i == 0) return;
		out.append(list, i, len);

		ArrayCache.getDefaultCache().putArray(list);

		list = out.list;
		len = out.len;
	}
	// endregion
	public final CharSequence subSequence(int start, int end) {
		checkBounds(start,end,len);
		if (start == 0 && end == len) return this;
		return start==end ? "" : new Slice(list, start, end);
	}
	public final String toString() { return toString(0, len); }
	public final String toString(int start, int end) {
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
		if (!(o instanceof CharSequence)) return false;

		CharSequence cs = (CharSequence) o;

		if (len != cs.length()) return false;
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
		private final char[] array;
		private final int off, len;

		public Slice(char[] array, int start, int end) {
			this.array = array;
			this.off = start;
			this.len = end-start;
		}

		public int length() { return len; }
		public char charAt(int i) {
			if (i >= len) throw new StringIndexOutOfBoundsException("off="+i+",len="+len);
			return array[off+i];
		}
		public CharSequence subSequence(int start, int end) {
			if (start == 0 && end == len) return this;
			checkBounds(start,end,len);

			return start==end ? "" : new Slice(array, start+off, end+off);
		}
		@Nonnull
		public String toString() { return new String(array, off, len); }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof CharSequence)) return false;

			CharSequence cs = (CharSequence) o;

			if (len != cs.length()) return false;

			int len = this.len+off;
			int j = 0;
			for (int i = off; i < len; i++) {
				if (array[i] != cs.charAt(j++)) {
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
				hash = 31 * hash + array[i];
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
			v2 = ArrayCache.getDefaultCache().getCharArray(512, false);
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
			ArrayCache.getDefaultCache().putArray(v2);
		}
		return len1 - len2;
	}
}