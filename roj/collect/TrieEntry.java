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
import java.util.Iterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/9 23:10
 */
public abstract class TrieEntry<T extends TrieEntry<T>> implements Iterable<T> {
    protected final char c;
    protected CharMap<T> children;

    protected TrieEntry(char ch) {
        this.c = ch;
        this.children = new CharMap<>(1, 1.5f);
    }

    public int childrenCount() {
        return children.size();
    }

    public void putChild(T e) {
        children.put(e.c, e);
    }

    public boolean removeChild(T e) {
        return children.remove(e.c) != null;
    }

    public T getChild(char c) {
        return children.get(c);
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return children.values().iterator();
    }

    public abstract int copyFrom(T node);

    protected abstract T newInstance();

    protected abstract int recursionSum();

    CharSequence text() {
        return null;
    }

    protected void append(CharList sb) {
        sb.append(c);
    }

    protected int length() {
        return 1;
    }

    @Override
    public String toString() {
        return "CE{" + c + "}";
    }
}
