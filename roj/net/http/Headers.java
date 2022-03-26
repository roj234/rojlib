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
package roj.net.http;

import roj.collect.IntMap;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.*;

/**
 * @author solo6975
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

    public static void encodeValue(Map<String, String> kvMap, StringBuilder sb) {
        Iterator<Map.Entry<String, String>> itr = kvMap.entrySet().iterator();
        while (true) {
            Map.Entry<String, String> entry = itr.next();
            sb.append(entry.getKey());
            String v = entry.getValue();

            f:
            if (v != null) {
                sb.append('=');
                for (int i = 0; i < v.length(); i++) {
                    switch (v.charAt(i)) {
                        case ' ':
                        case ',':
                        case '=':
                            sb.append('"').append(v).append('"');
                            break f;
                        case '"':
                            throw new IllegalArgumentException("'\"' Should not occur");
                    }
                }
                sb.append(v);
            }

            if (!itr.hasNext()) break;
            sb.append(',');
        }
    }

    public static MyHashMap<String, String> decodeValue(CharSequence field, boolean ordered) {
        MyHashMap<String, String> kvs = ordered ? new LinkedMyHashMap<>() : new MyHashMap<>();
        String k = null;
        int i = 0, j = 0;
        int flag = 0;
        while (i < field.length()) {
            char c = field.charAt(i++);
            if (flag == 1) {
                if (c == '"') {
                    if (k == null) throw new IllegalArgumentException("Escaped key");
                    kvs.put(k, Sub(field, j, i - 1));
                    j = i;
                    k = null;
                    flag = 2;
                }
            } else {
                switch (c) {
                    case '=':
                        if (k != null) throw new IllegalArgumentException("Unexpected '='");
                        k = Sub(field, j, i-1).toLowerCase();
                        j = i;
                        flag = 0;
                        break;
                    case '"':
                        flag = 1;
                        j = i;
                        break;
                    case ',':
                    case ';':
                    case ' ':
                        if (k != null) {
                            kvs.put(k, Sub(field, j, i - 1));
                            k = null;
                        } else {
                            if (i-1 > j) kvs.put(Sub(field, j, i - 1).toLowerCase(), null);
                            else if (c != ' ' && (flag & 2) == 0)
                                throw new IllegalArgumentException("'" + c + "' on empty");
                        }
                        flag = 0;
                        j = i;
                        break;
                }
            }
        }

        if (k != null) {
            kvs.put(k, Sub(field, j, i));
        } else if (i > j) {
            kvs.put(Sub(field, j, i).toLowerCase(), null);
        }

        return kvs;
    }

    private static String Sub(CharSequence c, int s, int e) {
        return c.subSequence(s, e).toString();
    }

    public Headers readFromLexer(HttpLexer wr) throws ParseException {
        while (true) {
            String k = wr.readHttpWord();
            if (k == HttpLexer._ERROR) {
                throw wr.err("Unexpected header error");
            } else if (k == HttpLexer._SHOULD_EOF) {
                break;
            } else if (k == null) {
                break;
            } else {
                String v = wr.readHttpWord();
                if (k.length() > 100 || v.length() > 500 || size > 100)
                    throw wr.err("Header too long");
                add(k, v);
            }
        }
        return this;
    }

    protected Entry<CharSequence, String> createEntry(CharSequence key) {
        return new E(key);
    }

    public List<String> getAll(final String s) {
        final E e = (E) this.getEntry(s);
        if (e == null) return null;

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

    public int getCount(String key) {
        E e = (E) getEntry(key);
        if (e == null) return 0;
        return 1 + e.all.size();
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

    public void set(String key, List<String> value) {
        E e = (E) getOrCreateEntry(key);
        if (e.v == IntMap.NOT_USING) size++;
        if (value.size() == 1) {
            e.all = Collections.emptyList();
        } else {
            e.all = new ArrayList<>(value);
            e.all.remove(0);
        }
        e.v = value.get(0);
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
        CharList tmp = IOUtil.getSharedCharBuf();

        int len = name.length();
        if (len == 0) {
            return tmp;
        }

        char[] cs = tmp.list;
        if (cs == null || cs.length < len) {
            cs = tmp.list = new char[len];
        }

        name.getChars(0, len, cs, 0);
        tmp.setLength(len);

        boolean dlm = true;
        for (int i = 0; i < len; ++i) {
            char c = cs[i];
            if (dlm) {
                if (c >= 'a' && c <= 'z') {
                    cs[i] = (char) (c - 32);
                }
                dlm = false;
            } else if (c >= 'A' && c <= 'Z') {
                cs[i] = (char) (c + 32);
            }
            if (c == '-') dlm = true;
        }
        return tmp;
    }

    public void encode(StringBuilder sb) {
        for (Map.Entry<CharSequence, String> entry : entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            if (entry instanceof E) {
                E e = (E) entry;
                for (String add : e.all) {
                    sb.append(entry.getKey()).append(": ").append(add).append("\r\n");
                }
            }
        }
    }

    public void encode(CharList sb) {
        for (Map.Entry<CharSequence, String> entry : entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            if (entry instanceof E) {
                E e = (E) entry;
                for (String add : e.all) {
                    sb.append(entry.getKey()).append(": ").append(add).append("\r\n");
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        encode(sb);
        return sb.toString();
    }
}