/*
 * This file is a part of MoreItems
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
package roj.net.tcp.util;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.text.CharList;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 傻逼GIT搞丢了我可怜的这个class，这是反编译出来的
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/23 21:20
 */
public final class Headers extends MyHashMap<CharSequence, String> {
    static final class E extends MyHashMap.Entry<CharSequence, String> {
        List<String> all;

        public E(CharSequence k) {
            super(k, null);
            this.all = Collections.emptyList();
        }
    }

    CharList tmp = new CharList();

    protected Entry<CharSequence, String> createEntry(CharSequence key) {
        return new E(key);
    }

    public List<String> getAll(final String s) {
        final E e = (E) this.getEntry(s);
        if (e == null) return Collections.emptyList();

        SimpleList<String> list = new SimpleList<>(e.all.size() + 1);
        list.add(e.v);
        list.addAll(e.all);
        return list;
    }

    public Entry<CharSequence, String> getEntry(CharSequence key) {
        return super.getEntry(capitalize(key.toString()));
    }

    public Entry<CharSequence, String> getOrCreateEntry(CharSequence key) {
        Entry<CharSequence, String> entry = super.getOrCreateEntry(capitalize(key.toString()));
        if (entry.v == IntMap.NOT_USING) {
            entry.k = entry.k.toString();
        }
        return entry;
    }

    public String getOne(String key, int index) {
        final E e = (E) getEntry(key);
        if (e == null) return null;
        return (index == 0) ? e.v : e.all.get(index - 1);
    }

    public void add(String key, String value) {
        E e = (E) getOrCreateEntry(key);
        if (e.v == IntMap.NOT_USING) {
            e.v = value;
            size++;
        } else {
            if (e.all.isEmpty())
                e.all = new LinkedList<>();
            e.all.add(value);
        }
    }

    public void set(String key, String value) {
        E e = (E) getOrCreateEntry(key);
        if (e.v == IntMap.NOT_USING)
            size++;
        e.all = Collections.emptyList();
        e.v = value;
    }

    public List<String> removeAll(String key) {
        E entry = (E) getEntry(key);
        if (entry == null) return Collections.emptyList();

        SimpleList<String> list = new SimpleList<>(entry.all.size() + 1);
        list.add(entry.v);
        list.addAll(entry.all);

        super.remove(key);
        return list;
    }

    private CharList capitalize(String name) {
        tmp.clear();

        int len = name.length();
        if (len == 0) {
            return tmp;
        }

        char[] tmp = this.tmp.list;
        if (tmp == null || tmp.length < len) {
            tmp = this.tmp.list = new char[len];
        }

        name.getChars(0, len, tmp, 0);
        this.tmp.setIndex(len);

        boolean dlm = true;
        for (int i = 0; i < len; ++i) {
            char c = tmp[i];
            if (dlm) {
                if (c >= 'a' && c <= 'z') {
                    tmp[i] = (char) (c - 32);
                }
                dlm = false;
            } else if (c >= 'A' && c <= 'Z') {
                tmp[i] = (char) (c + 32);
            }
            if (c == '-') dlm = true;
        }
        return this.tmp;
    }
}