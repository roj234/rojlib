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
package roj.collect;

import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Roj234
 * @version 0.1
 * @since  2020/11/9 23:10
 */
public abstract class TrieEntry implements Iterable<TrieEntry> {
    final char c;

    private TrieEntry next;

    TrieEntry(char ch) {
        this.c = ch;
    }

    public int childrenCount() {
        return size;
    }

    public final void putChild(TrieEntry te) {
        if (size > length * 1.3f) {
            length <<= 1;
            resize();
        }

        char key = te.c;
        if (entries == null)
            entries = new TrieEntry[length];
        TrieEntry prev = null, entry = entries[idx(key)];
        if (entry == null) {
            entries[idx(key)] = te;
            return;
        }
        while (true) {
            if (entry.c == key) {
                if (prev == null) {
                    entries[idx(key)] = te;
                } else {
                    prev.next = te;
                }
                te.next = entry.next;
                return;
            }
            if (entry.next == null)
                break;
            prev = entry;
            entry = entry.next;
        }
        entry.next = te;
        size++;
    }

    public final boolean removeChild(TrieEntry te) {
        TrieEntry prevEntry = null;
        TrieEntry entry = first(te.c);
        while (entry != null) {
            if (entry.c == te.c) {
                break;
            }
            prevEntry = entry;
            entry = entry.next;
        }

        if (entry == null)
            return false;

        this.size--;

        if (prevEntry != null) {
            prevEntry.next = entry.next;
        } else {
            this.entries[idx(te.c)] = entry.next;
        }

        entry.next = null;

        return true;
    }

    public final TrieEntry getChild(char key) {
        TrieEntry entry = first(key);
        while (entry != null) {
            if (entry.c == key)
                return entry;
            entry = entry.next;
        }
        return null;
    }

    @Nonnull
    @Override
    public final Iterator<TrieEntry> iterator() {
        return new ValItr(this);
    }

    public abstract int copyFrom(TrieEntry node);

    abstract TrieEntry newInstance();

    abstract int recursionSum();

    CharSequence text() {
        return null;
    }

    void append(CharList sb) {
        sb.append(c);
    }

    int length() {
        return 1;
    }

    @Override
    public String toString() {
        return "CE{" + c + "}";
    }

    TrieEntry[] entries;
    int size = 0;
    int length = 1;

    private void resize() {
        TrieEntry[] newEntries = new TrieEntry[length];
        TrieEntry entry;
        TrieEntry next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = entries[i];
            while (entry != null) {
                next = entry.next;
                int newKey = idx(entry.c);
                TrieEntry entry2 = newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = entry2;
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    private int idx(int id) {
        return (id ^ (id >> 8)) & (length - 1);
    }

    private TrieEntry first(char k) {
        if (entries == null) {
            return null;
        }
        return entries[idx(k)];
    }

    public final void clear() {
        if (size == 0) return;
        size = 0;
        if (entries != null)
            Arrays.fill(entries, null);
    }

    static final class ValItr extends AbstractIterator<TrieEntry> {
        TrieEntry map, entry;
        int i;

        public ValItr(TrieEntry map) {
            this.map = map;
            if (map.entries == null)
                stage = ENDED;
        }

        @Override
        public boolean computeNext() {
            TrieEntry[] entries = map.entries;
            while (true) {
                if (entry != null) {
                    result = entry;
                    entry = entry.next;
                    return true;
                } else {
                    if (i < entries.length) {
                        this.entry = entries[i++];
                    } else {
                        return false;
                    }
                }
            }
        }
    }

    public void resetMap() {
        this.length = 1;
        this.entries = null;
        this.size = 0;
    }
}
