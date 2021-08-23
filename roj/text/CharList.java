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
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/19 1:28
 */
public class CharList implements CharSequence {
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
            char[] newList = new char[Math.max((int) (required * 1.5), 32)];
            if (list != null && ptr > 0)
                System.arraycopy(list, 0, newList, 0, Math.min(ptr, list.length));
            list = newList;
        }
    }

    public void append(char[] array) {
        if(array.length == 0) return;
        append(array, 0, array.length);
    }

    public void append(char[] array, int start, int length) {
        if (start + length > array.length) {
            throw new ArrayIndexOutOfBoundsException("Source[" + (start + length) + "] of " + array.length);
        }
        if (start < 0)
            throw new ArrayIndexOutOfBoundsException("Source[" + start + "] of " + array.length);

        ensureCapacity(ptr + length);
        System.arraycopy(array, start, list, ptr, length);
        ptr += length;
    }

    public void append(CharList array) {
        if(array.length() == 0) return;
        append(array.list, 0, array.ptr);
    }

    public void append(CharList array, int start, int length) {
        append(array.list, start, length);
    }

    public CharList append(Object cs) {
        return append(String.valueOf(cs));
    }

    public CharList append(CharSequence cs) {
        return append(cs, 0, cs.length());
    }

    public CharList append(CharSequence cs, int start, int length) {
        if (cs instanceof CharList) {
            append(((CharList) cs).list, start, length);
            return this;
        }

        ensureCapacity(ptr + length);

        char[] list = this.list;
        int j = ptr;

        length += start;

        for (int i = start; i < length; i++) {
            list[j++] = cs.charAt(i);
        }
        ptr = j;

        return this;
    }

    public void set(int index, char e) {
        list[index] = e;
    }

    public char charAt(int _id) {
        if (_id >= ptr)
            throw new StringIndexOutOfBoundsException("Required " + _id + " Current " + ptr);
        return list[_id]; // 2
    }

    public void setIndex(int id) {
        if(list == null)
            throw new ArrayIndexOutOfBoundsException(id);
        if (id > list.length)
            throw new ArrayIndexOutOfBoundsException("Required " + id + " Current " + list.length);
        this.ptr = id;
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
        return formString(0, ptr);
    }

    @Override
    public CharList subSequence(int start, int end) {
        int len = length();

        if (start == 0 && end == len) {
            return this;
        }

        if (0 <= start && start <= end && end <= len) {
            return new ReadOnlySubList(list, start + getOffset(), end - start);
        } else {
            throw new StringIndexOutOfBoundsException(start);
        }
    }

    public void clear() {
        ptr = 0;
    }

    public CharList replace(char a, char b) {
        char[] list = this.list;
        for (int i = getOffset(); i < ptr; i++) {
            if (list[i] == a) {
                list[i] = b;
            }
        }
        return this;
    }

    public CharList replace(char o, char n, int i, int len) {
        char[] list = this.list;
        for (i += getOffset(); i < len; i++) {
            if (list[i] == o) {
                list[i] = n;
            }
        }
        return this;
    }

    public void replace(int start, int end, CharSequence s) {
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        } else {
            if (start > end) {
                throw new StringIndexOutOfBoundsException();
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

    public CharList replace(CharSequence source, CharSequence target) {
        int pos = 0;
        while ((pos = indexOf(source, pos)) != -1) {
            replace(pos, pos + source.length(), target);
            pos += source.length();
        }
        return this;
    }

    public int indexOf(CharSequence sequence, int offset) {
        int i = offset + getOffset();

        o:
        for (; i < ptr; i++) {
            if (list[i] == sequence.charAt(0)) {
                for (int j = 0; j < sequence.length(); j++) {
                    if(list[i + j] != sequence.charAt(j)) {
                        continue o;
                    }
                }
                return i;
            }
        }

        return -1;
    }

    public int getOffset() {
        return 0;
    }

    public String formString(int start, int length) {
        return length == 0 ? "" : new String(list, start + getOffset(), start - getOffset() + length);
    }

    @Override
    public IntStream chars() {
        return StreamSupport.intStream(spliterator(), false);
    }

    public Spliterator.OfInt spliterator() {
        return new CharArraySpliterator(list, getOffset(), ptr, 0);
    }

    public boolean regionMatches(int index, CharSequence sequence) {
        return regionMatches(index, sequence, 0, sequence.length());
    }

    public boolean regionMatches(int index, CharSequence sequence, int offset) {
        return regionMatches(index, sequence, offset, sequence.length());
    }

    public boolean regionMatches(int index, CharSequence sequence, int offset, int length) {
        if (index + length > ptr)
            return false;

        for (int i = index + getOffset(); offset < length; i++, offset++) {
            if (list[i] != sequence.charAt(offset))
                return false;
        }

        return true;
    }

    public void delete(int start, int end) {
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        } else {
            if (start > end) {
                throw new StringIndexOutOfBoundsException();
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

    static final class CharArraySpliterator implements Spliterator.OfInt {
        private final char[] array;
        private int index;        // current index, modified on advance/split
        private final int fence;  // one past last index
        private final int characteristics;

        public CharArraySpliterator(char[] array, int addChar) {
            this(array, 0, array.length, addChar);
        }

        public CharArraySpliterator(char[] array, int origin, int fence, int addChar) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.characteristics = addChar | Spliterator.SIZED | Spliterator.SUBSIZED;
        }

        @Override
        public OfInt trySplit() {
            int lo = index, mid = (lo + fence) >>> 1;
            return (lo >= mid)
                    ? null
                    : new CharArraySpliterator(array, lo, index = mid, characteristics);
        }

        @Override
        public void forEachRemaining(IntConsumer action) {
            if (action == null)
                throw new NullPointerException();
            char[] a;
            int i, hi; // hoist accesses and checks from loop
            if ((a = array).length >= (hi = fence) &&
                    (i = index) >= 0 && i < (index = hi)) {
                do {
                    action.accept(a[i]);
                } while (++i < hi);
            }
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (action == null)
                throw new NullPointerException();
            if (index >= 0 && index < fence) {
                action.accept(array[index++]);
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() {
            return fence - index;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }

        @Override
        public Comparator<? super Integer> getComparator() {
            if (hasCharacteristics(Spliterator.SORTED))
                return null;
            throw new IllegalStateException();
        }
    }

    /**
     * 只读
     */
    public final static class ReadOnlySubList extends CharList {
        private final int offset;

        public ReadOnlySubList(char[] array, int start, int length) {
            super(array);
            this.ptr = start + length;
            this.offset = start;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Readonly");
        }

        @Override
        public int length() {
            return ptr - offset;
        }

        @Override
        public void delete(int e) {
            throw new UnsupportedOperationException("Readonly");
        }

        @Override
        public CharList append(char e) {
            throw new UnsupportedOperationException("Readonly");
        }

        public void ensureCapacity(int required) {
            if (required > list.length) {
                throw new ArrayIndexOutOfBoundsException("Required " + required + " Current " + list.length);
            }
        }

        public void setIndex(int id) {
            throw new UnsupportedOperationException("Readonly");
        }

        @Override
        public char charAt(int index) {
            return super.charAt(index + offset);
        }

        @Override
        public void append(char[] array, int start, int length) {
            throw new UnsupportedOperationException("Readonly");
        }

        @Override
        public CharList append(CharSequence cs, int start, int length) {
            throw new UnsupportedOperationException("Readonly");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CharSequence)) return false;

            CharSequence cs = (CharSequence) o;

            if (ptr - offset != cs.length()) return false;
            final char[] list = this.list;
            for (int i = offset; i < ptr; i++) {
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
            for (int i = offset; i < ptr; i++) {
                hash = 31 * hash + list[i];
            }
            return hash;
        }
    }
}