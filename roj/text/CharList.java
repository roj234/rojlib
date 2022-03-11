/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package roj.text;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * @author Roj234
 * @since 2021/6/19 1:28
 */
public class CharList implements CharSequence, Appendable {
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

    public void delete(int index) {
        delete(0, 1);
    }

    public void ensureCapacity(int required) {
        if (list == null || required > list.length) {
            char[] newList = new char[Math.max(((required * 3) >> 1), 32)];
            if (list != null && ptr > 0)
                System.arraycopy(list, 0, newList, 0, Math.min(ptr, list.length));
            list = newList;
        }
    }

    public CharList append(char[] array) {
        return append(array, 0, array.length);
    }

    public CharList append(char[] c, int start, int end) {
        if (start < 0 || end > c.length || c.length < end - start) {
            throw new StringIndexOutOfBoundsException("len="+c.length+",str="+start+",end="+end);
        }
        int length = end - start;
        if (length == 0) return this;
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
        return append(cs, 0, cs.length());
    }

    public CharList append(String s) {
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

    public void set(int index, char e) {
        list[index] = e;
    }

    public char charAt(int i) {
        if (i >= ptr)
            throw new StringIndexOutOfBoundsException("len="+ptr+",off="+i);
        return list[i]; // 2
    }

    public void setLength(int i) {
        if(list == null) {
            if(i == 0) return;
            throw new StringIndexOutOfBoundsException("len=0,off="+i);
        }
        if (i > list.length)
            throw new StringIndexOutOfBoundsException("len="+list.length+",off="+i);
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
            if(list[i] != cs.charAt(i)) {
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
            return end == start ? "" : new Slice(list, start, end - start);
        } else {
            throw new StringIndexOutOfBoundsException("len="+len+",str="+start+",end="+end);
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
            throw new StringIndexOutOfBoundsException("len="+s.length()+",str="+start+",end="+end);
        } else {
            if (start > end) {
                throw new StringIndexOutOfBoundsException("len="+s.length()+",str="+start+",end="+end);
            } else {
                if(end > this.ptr)
                    end = this.ptr;

                int origLen = end - start;
                if (origLen > 0) {
                    if(origLen > s.length()) {
                        int delta = s.length() - origLen; // < 0
                        System.arraycopy(list, end, list, end + delta, this.ptr - end);
                        this.ptr += delta;
                        end = start + s.length();
                    } else if(origLen < s.length()) {
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

    public CharList replace(CharSequence str, CharSequence target) {
        int pos = 0;
        while ((pos = indexOf(str, pos)) != -1) {
            replace(pos, pos + str.length(), target);
            pos += target.length();
        }
        return this;
    }

    public int indexOf(CharSequence str, int from) {
        int i = from;

        char[] list = this.list;
        o:
        for (; i < ptr; i++) {
            if (list[i] == str.charAt(0)) {
                for (int j = 0; j < str.length(); j++) {
                    if(list[i + j] != str.charAt(j)) {
                        continue o;
                    }
                }
                return i;
            }
        }

        return -1;
    }

    public char[] toCharArray() {
        return Arrays.copyOf(list, ptr);
    }

    public String toString(int start, int length) {
        return length == 0 ? "" : new String(list, start, start + length);
    }

    @Override
    public IntStream chars() {
        return StreamSupport.intStream(new CharArraySpliterator(list, 0, ptr, Spliterator.ORDERED), false);
    }

    public boolean regionMatches(int index, CharSequence str) {
        return regionMatches(index, str, 0, str.length());
    }

    public boolean regionMatches(int index, CharSequence str, int offset) {
        return regionMatches(index, str, offset, str.length());
    }

    public boolean regionMatches(int index, CharSequence str, int off, int length) {
        if (index + length > ptr)
            return false;

        char[] list = this.list;
        for (int i = index; off < length; i++, off++) {
            if (list[i] != str.charAt(off))
                return false;
        }

        return true;
    }

    public void delete(int start, int end) {
        if (start < 0) {
            throw new StringIndexOutOfBoundsException("len="+ptr+",str="+start+",end="+end);
        } else {
            if (start > end) {
                throw new StringIndexOutOfBoundsException("len="+ptr+",str="+start+",end="+end);
            } else {
                if(end > this.ptr)
                    end = this.ptr;

                int delta = end - start;
                if (delta > 0) {
                    if(end != this.ptr)
                        System.arraycopy(this.list, start + delta, this.list, start, this.ptr - end);
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
        if(st > 0) {
            delete(0, st);
        }

        return this;
    }

    public CharList insert(int pos, char str) {
        ensureCapacity(1 + ptr);
        if(ptr - pos > 0)
            System.arraycopy(list, pos, list, pos + 1, ptr - pos);
        list[pos] = str;
        ptr ++;
        return this;
    }

    public CharList insert(int pos, CharSequence str) {
        return insert(pos, str, 0, str.length());
    }

    public CharList insert(int pos, CharSequence s, int str, int end) {
        int len = end - str;
        ensureCapacity(len + ptr);
        char[] list = this.list;
        if(ptr - pos > 0 && len > 0)
            System.arraycopy(list, pos, list, pos + len, ptr - pos);
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

    /**
     * 只读
     */
    public final static class Slice implements CharSequence {
        private final char[] array;
        private final int off, len;

        public Slice(char[] array, int start, int length) {
            this.array = array;
            this.off = start;
            this.len = length;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            if (start == 0 && end == len) {
                return this;
            }

            if (0 <= start && start <= end && end <= len) {
                return end == start ? "" : new Slice(array, off + start, end - start);
            } else {
                throw new StringIndexOutOfBoundsException("len="+len+",str="+start+",end="+end);
            }
        }

        @Override
        public String toString() {
            return new String(array, off, len);
        }

        @Override
        public IntStream chars() {
            return StreamSupport.intStream(new CharArraySpliterator(array, off, off+len, Spliterator.ORDERED), false);
        }

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int i) {
            if (i - off > len)
                throw new StringIndexOutOfBoundsException("len="+len+",off="+i);
            return array[off + i];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CharSequence)) return false;

            CharSequence cs = (CharSequence) o;

            if (len != cs.length()) return false;
            char[] list = this.array;
            int len = this.len + off;
            for (int i = off; i < len; i++) {
                if(list[i] != cs.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 0;

            char[] list = this.array;
            int len = this.len + off;
            for (int i = off; i < len; i++) {
                hash = 31 * hash + list[i];
            }
            return hash;
        }
    }
}