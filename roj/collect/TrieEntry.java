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
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.Iterator;

/**
 * @author Roj234
 * @version 0.1
 * @since  2020/11/9 23:10
 */
public abstract class TrieEntry implements Iterable<TrieEntry>, Cloneable {
    final char c;

    private TrieEntry next;

    TrieEntry(char ch) {
        this.c = ch;
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
            size++;
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
        return new Val(this);
    }

    public abstract int copyFrom(TrieEntry node);

    abstract boolean isValid();

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
    int size;
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
        this.length = 1;
        this.entries = null;
        this.size = 0;
    }

    @Override
    public TrieEntry clone() {
        TrieEntry entry = null;
        try {
            entry = (TrieEntry) super.clone();
        } catch (CloneNotSupportedException ignored) {}
        entry.clear();
        return entry;
    }

    static final class Val extends AbstractIterator<TrieEntry> {
        TrieEntry map, entry;
        int i;

        public Val(TrieEntry map) {
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

    static abstract class Itr<NEXT, ENTRY extends TrieEntry> extends AbstractIterator<NEXT> {
        SimpleList<ENTRY> a = new SimpleList<>(), b = new SimpleList<>();
        ENTRY ent;
        int   i = 0;

        public final void setupDepthFirst(ENTRY root) {
            a.add(root);
            b.add(Helpers.cast(root.iterator()));
        }

        public final void setupWidthFirst(ENTRY root) {
            a.add(root);
        }

        // 深度优先
        @SuppressWarnings("unchecked")
        public final boolean _computeNextDepthFirst() {
            SimpleList<ENTRY> a = this.a;
            SimpleList<Iterator<ENTRY>> b = Helpers.cast(this.b);

            int i = a.size - 1;
            while (true) {
                ENTRY ent = a.get(i);
                if (this.ent != ent) {
                    if (ent.isValid()) {
                        this.ent = ent;
                        return true;
                    }
                }

                Iterator<ENTRY> itr = b.get(i);
                if (!itr.hasNext()) { // this level is empty
                    do {
                        a.remove(i);
                        b.remove(i--);
                        if (i < 0) return false;
                    } while (!b.get(i).hasNext());
                } else {
                    ENTRY t = itr.next();
                    a.add(t);
                    b.add((Iterator<ENTRY>) t.iterator());
                    i++;
                }
            }
        }

        // 广度优先
        @SuppressWarnings("unchecked")
        public final boolean _computeNextWidthFirst() {
            SimpleList<ENTRY> a = this.a;
            while (true) {
                if (a.size == 0) return false;

                while (i < a.size) {
                    if ((ent = a.get(i++)).isValid()) return true;
                }
                i = 0;

                SimpleList<ENTRY> b = this.b;
                for (ENTRY entry : a) {
                    if (entry.size > 0) {
                        b.ensureCapacity(b.size + entry.size);
                        for (TrieEntry subEntry : entry) {
                            b.add((ENTRY) subEntry);
                        }
                    }
                }

                // Swap
                a.size = 0;
                this.a = this.b;
                this.b = a;
                a = this.a;
            }
        }
    }

    static final class KeyItr extends Itr<CharSequence, TrieEntry> {
        final CharList seq;

        KeyItr(TrieEntry root) {
            this(root, new CharList());
        }

        KeyItr(TrieEntry root, CharList base) {
            super.setupDepthFirst(root);
            result = seq = base;
            i = base.length();
        }

        @Override
        public boolean computeNext() {
            boolean v = super._computeNextDepthFirst();
            if (v) {
                seq.setIndex(i);
                // j = 1: skip root entry
                for (int j = 1; j < a.size; j++) {
                    a.get(j).append(seq);
                }
            }
            return v;
        }
    }

}
