package roj.text;

import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.math.MutableInt;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2021/6/19 1:28
 */
public class CharList implements CharSequence, Appendable {
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

	static int stringSize(long x) {
		long p = 10;
		for (int i=1; i<19; i++) {
			if (x < p)
				return i;
			p = 10*p;
		}
		return 19;
	}

	private final static int [] sizeTable = { 9, 99, 999, 9999, 99999, 999999, 9999999,
									  99999999, 999999999, Integer.MAX_VALUE };
	static int stringSize(int x) {
		for (int i=0; ; i++)
			if (x <= sizeTable[i])
				return i+1;
	}
	// endregion

	public char[] list;
	protected int ptr;

	public CharList() {
		this.ptr = 0;
	}

	public CharList(int len) {
		list = new char[len];
	}

	public CharList(char[] array) {
		list = array;
		ptr = array.length;
	}

	public int arrayLength() {
		return list.length;
	}

	public int length() {
		return ptr;
	}

	public CharList append(char e) {
		int i = this.ptr++;
		ensureCapacity(this.ptr);
		list[i] = e;
		return this;
	}

	public final CharList readFully(Reader in) throws IOException {
		return readFully(in, true);
	}

	public final CharList readFully(Reader in, boolean close) throws IOException {
		ensureCapacity(ptr+127);

		int r;
		do {
			r = in.read(list, ptr, list.length - ptr);
			if (r <= 0) break;
			ptr += r;
			ensureCapacity(ptr+1);
		} while (true);
		if (close) in.close();
		return this;
	}

	public void delete(int index) {
		delete(index, index+1);
	}

	public void ensureCapacity(int required) {
		if (list == null || required > list.length) {
			char[] newList = new char[Math.max(((required * 3) >> 1), 32)];
			if (list != null && ptr > 0) System.arraycopy(list, 0, newList, 0, Math.min(ptr, list.length));
			list = newList;
		}
	}

	public CharList append(char[] array) {
		return append(array, 0, array.length);
	}
	public CharList append(char[] c, int start, int end) {
		int length = end - start;
		if (length == 0) return this;

		if (start < 0 || end > c.length || c.length < end - start || start > end) {
			throw new StringIndexOutOfBoundsException("len=" + c.length + ",str=" + start + ",end=" + end);
		}
		ensureCapacity(ptr + length);
		System.arraycopy(c, start, list, ptr, length);
		ptr += length;
		return this;
	}

	public CharList append(CharList list) {
		return append(list.list, 0, list.ptr);
	}
	public CharList append(CharList list, int start, int end) {
		return append(list.list, start, end);
	}

	public CharList append(Object cs) {
		return append(String.valueOf(cs));
	}

	public CharList append(CharSequence cs) {
		if (cs == null) return append("null");
		return append(cs, 0, cs.length());
	}

	public CharList append(String s) {
		if (s == null) s = "null";
		ensureCapacity(ptr + s.length());
		s.getChars(0, s.length(), list, ptr);
		ptr += s.length();
		return this;
	}

	public CharList append(CharSequence cs, int start, int end) {
		if (cs instanceof CharList) {
			return append(((CharList) cs).list, start, end);
		}
		if (cs instanceof Slice) {
			Slice s = (Slice) cs;
			return append(s.array, s.off + start, s.off + end);
		}

		ensureCapacity(ptr + end - start);

		char[] list = this.list;
		int j = ptr;

		for (int i = start; i < end; i++) {
			list[j++] = cs.charAt(i);
		}
		ptr = j;

		return this;
	}

	public CharList append(int i) {
		if (i == Integer.MIN_VALUE) {
			append("-2147483648");
			return this;
		}

		int len = (i < 0) ? stringSize(-i)+1 : stringSize(i);
		int end = ptr+len;
		ensureCapacity(end);

		if (i < 0) {
			list[ptr] = '-';
			i = -i;
		}

		getChars(i, end, list);

		ptr = end;
		return this;
	}

	public CharList append(long l) {
		if (l == Long.MIN_VALUE) {
			append("-9223372036854775808");
			return this;
		}

		int len = (l < 0) ? stringSize(-l)+1 : stringSize(l);
		int end = ptr+len;
		ensureCapacity(end);

		if (l < 0) {
			list[ptr] = '-';
			l = -l;
		}
		getChars(l, end, list);

		ptr = end;
		return this;
	}

	public void set(int index, char e) {
		list[index] = e;
	}

	public char charAt(int i) {
		if (i >= ptr) throw new StringIndexOutOfBoundsException("len=" + ptr + ",off=" + i);
		return list[i]; // 2
	}

	public void setLength(int i) {
		if (list == null) {
			if (i == 0) return;
			throw new StringIndexOutOfBoundsException("len=0,off=" + i);
		}
		if (i > list.length) throw new StringIndexOutOfBoundsException("len=" + list.length + ",off=" + i);
		this.ptr = i;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CharSequence)) return false;

		CharSequence cs = (CharSequence) o;

		if (ptr != cs.length()) return false;
		final char[] list = this.list;
		for (int i = 0; i < ptr; i++) {
			if (list[i] != cs.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 0;

		final char[] list = this.list;
		for (int i = 0; i < ptr; i++) {
			hash = 31 * hash + list[i];
		}
		return hash;
	}

	@Nonnull
	public String toString() {
		return toString(0, ptr);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		int len = ptr;

		if (start == 0 && end == len) {
			return this;
		}

		if (0 <= start && start <= end && end <= len) {
			return end == start ? "" : new Slice(list, start, end);
		} else {
			throw new StringIndexOutOfBoundsException("len=" + len + ",str=" + start + ",end=" + end);
		}
	}

	public void clear() {
		ptr = 0;
	}

	public CharList replace(char a, char b) {
		return replace(a, b, 0, ptr);
	}

	public CharList replace(char o, char n, int i, int len) {
		char[] list = this.list;
		for (; i < len; i++) {
			if (list[i] == o) {
				list[i] = n;
			}
		}
		return this;
	}

	public void replace(int start, int end, CharSequence s) {
		if (start < 0) {
			throw new StringIndexOutOfBoundsException("len=" + s.length() + ",str=" + start + ",end=" + end);
		} else {
			if (start > end) {
				throw new StringIndexOutOfBoundsException("len=" + s.length() + ",str=" + start + ",end=" + end);
			} else {
				if (end > this.ptr) end = this.ptr;

				int origLen = end - start;
				if (origLen > 0) {
					if (origLen > s.length()) {
						int delta = s.length() - origLen; // < 0
						System.arraycopy(list, end, list, end + delta, this.ptr - end);
						this.ptr += delta;
						end = start + s.length();
					} else if (origLen < s.length()) {
						insert(end, s, origLen, s.length());
					}

					char[] list = this.list;
					int j = 0;
					while (start < end) {
						list[start++] = s.charAt(j++);
					}
				}
			}
		}
	}

	/**
	 * 不在替换结果中搜索
	 * "aaaa".replace("aa","a") => "aa"
	 */
	public CharList replace(CharSequence str, CharSequence target) {
		int pos = 0;
		while ((pos = indexOf(str, pos)) != -1) {
			replace(pos, pos+str.length(), target);
			pos += target.length();
		}
		return this;
	}
	/**
	 * 在替换结果中搜索
	 * "aaaa".replaceInReplaceResult("aa","a") => "a"
	 */
	public CharList replaceInReplaceResult(CharSequence str, CharSequence target) {
		int pos = 0;
		while ((pos = indexOf(str, pos)) != -1) {
			replace(pos, pos+str.length(), target);
		}
		return this;
	}
	public CharList replaceMulti(String[] str, String[] target) {
		TrieTree<String> map = new TrieTree<>();
		for (int i = 0; i < str.length; i++) {
			map.put(str[i], target[i]);
		}
		int pos = 0;

		MyHashMap.Entry<MutableInt, String> entry = new MyHashMap.Entry<>(new MutableInt(), null);
		while (true) {
			map.longestIn(this, pos, ptr, entry);
			int len = entry.getKey().getValue();
			if (len < 0) break;

			replace(pos, pos+len, entry.getValue());
			pos += len;
		}

		return this;
	}

	public int indexOf(CharSequence str) {
		return indexOf(str, 0);
	}
	public int indexOf(CharSequence str, int from) {
		return findForward(str, from, ptr-str.length()+1);
	}

	private int findForward(CharSequence str, int from, int to) {
		int i = from;

		char[] list = this.list;
		o:
		for (; i < to; i++) {
			if (list[i] == str.charAt(0)) {
				for (int j = 1; j < str.length(); j++) {
					if (list[i + j] != str.charAt(j)) {
						continue o;
					}
				}
				return i;
			}
		}

		return -1;
	}

	public int lastIndexOf(CharSequence str) {
		return lastIndexOf(str, 0);
	}

	public int lastIndexOf(CharSequence str, int to) {
		int i = ptr - str.length();

		char[] list = this.list;
		o:
		for (; i >= to; i--) {
			if (list[i] == str.charAt(0)) {
				for (int j = 1; j < str.length(); j++) {
					if (list[i + j] != str.charAt(j)) {
						continue o;
					}
				}
				return i;
			}
		}

		return -1;
	}

	public boolean startsWith(CharSequence str) {
		return str.length() == 0 || findForward(str, 0, 1) >= 0;
	}
	public boolean endsWith(CharSequence str) {
		return str.length() == 0 || findForward(str, ptr-str.length(), ptr-str.length()+1) >= 0;
	}

	public char[] toCharArray() {
		return Arrays.copyOf(list, ptr);
	}

	public String toString(int start, int end) {
		return end <= start ? "" : new String(list, start, end-start);
	}

	public boolean regionMatches(int index, CharSequence str) {
		return regionMatches(index, str, 0, str.length());
	}

	public boolean regionMatches(int index, CharSequence str, int offset) {
		return regionMatches(index, str, offset, str.length());
	}

	public boolean regionMatches(int index, CharSequence str, int off, int length) {
		if (index + length > ptr) return false;

		char[] list = this.list;
		for (int i = index; off < length; i++, off++) {
			if (list[i] != str.charAt(off)) return false;
		}

		return true;
	}

	public void delete(int start, int end) {
		if (start < 0) {
			throw new StringIndexOutOfBoundsException("len=" + ptr + ",str=" + start + ",end=" + end);
		} else {
			if (start > end) {
				throw new StringIndexOutOfBoundsException("len=" + ptr + ",str=" + start + ",end=" + end);
			} else {
				if (end > this.ptr) end = this.ptr;

				int delta = end - start;
				if (delta > 0) {
					if (end != this.ptr) System.arraycopy(this.list, start + delta, this.list, start, this.ptr - end);
					this.ptr -= delta;
				}
			}
		}
	}

	public CharList trim() {
		int len = ptr;
		int st = 0;
		char[] val = list;    /* avoid getfield opcode */

		while ((st < len) && (val[st] <= ' ')) {
			st++;
		}
		while ((st < len) && (val[len - 1] <= ' ')) {
			len--;
		}

		ptr = len;
		if (st > 0) {
			delete(0, st);
		}

		return this;
	}

	public CharList insert(int pos, char str) {
		ensureCapacity(1 + ptr);
		if (ptr - pos > 0) System.arraycopy(list, pos, list, pos + 1, ptr - pos);
		list[pos] = str;
		ptr++;
		return this;
	}

	public CharList insert(int pos, CharSequence str) {
		return insert(pos, str, 0, str.length());
	}

	public CharList insert(int pos, CharSequence s, int str, int end) {
		int len = end - str;
		ensureCapacity(len + ptr);
		char[] list = this.list;
		if (ptr - pos > 0 && len > 0) System.arraycopy(list, pos, list, pos + len, ptr - pos);
		while (str < end) {
			list[pos++] = s.charAt(str++);
		}
		ptr += len;

		return this;
	}

	public CharList appendCodePoint(int cp) {
		return append(Character.highSurrogate(cp)).append(Character.lowSurrogate(cp));
	}

	public boolean contains(CharSequence s) {
		return indexOf(s, 0) != -1;
	}

	public CharBuffer toCharBuffer() {
		return CharBuffer.wrap(list, 0, ptr);
	}

	/**
	 * 只读
	 */
	public final static class Slice implements CharSequence {
		private final char[] array;
		private final int off, end;

		public Slice(char[] array, int start, int end) {
			this.array = array;
			this.off = start;
			this.end = end;
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			if (start == 0 && end == this.end - off) return this;

			if (0 <= start && start <= end && end <= this.end - off) {
				return end == start ? "" : new Slice(array, start + off, end + this.end);
			} else {
				throw new StringIndexOutOfBoundsException("len=" + (this.end - off) + ",str=" + start + ",end=" + end);
			}
		}

		@Override
		public String toString() {
			return new String(array, off, end - off);
		}

		@Override
		public int length() {
			return end - off;
		}

		@Override
		public char charAt(int i) {
			if (i >= end - off) throw new StringIndexOutOfBoundsException("len=" + (end - off) + ",idx=" + i);
			return array[off + i];
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof CharSequence)) return false;

			CharSequence cs = (CharSequence) o;

			if (end - off != cs.length()) return false;

			char[] list = array;
			int j = 0;
			for (int i = off; i < end; i++) {
				if (list[i] != cs.charAt(j++)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int hashCode() {
			int hash = 0;

			char[] list = array;
			for (int i = off; i < end; i++) {
				hash = 31 * hash + list[i];
			}
			return hash;
		}
	}
}