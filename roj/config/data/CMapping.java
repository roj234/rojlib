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
package roj.config.data;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.serial.StreamSerializer;
import roj.config.serial.Structs;
import roj.config.word.AbstLexer;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CMapping extends CEntry {
    final   Map<String, CEntry> map;
    private CharList            dot;

    public CMapping() {
        this.map = new /*Linked*/MyHashMap<>();
    }

    public CMapping(Map<String, CEntry> map) {
        this.map = map;
    }

    public CMapping(int size) {
        this.map = new MyHashMap<>(size);
    }

    public final int size() {
        return map.size();
    }

    public CharList dot(boolean dotMode) {
        if (dotMode == (dot == null))
            this.dot = dotMode ? new CharList() : null;
        return dot;
    }

    public final Map<String, CEntry> raw() {
        return map;
    }

    @Override
    public Type getType() {
        return Type.MAP;
    }

    public final Set<String> keySet() {
        return map.keySet();
    }

    public final Set<Map.Entry<String, CEntry>> entrySet() {
        return map.entrySet();
    }

    public final Collection<CEntry> values() {
        return map.values();
    }

    // region PUT

    public final CEntry put(String key, CEntry entry) {
        return put1(key, entry == null ? CNull.NULL : entry, 2);
    }

    public final CEntry put(String key, String entry) {
        if (entry == null) return map.remove(key);

        CEntry prev = get(key);
        if (prev.getType() == Type.STRING) {
            ((CString) prev).value = entry;
            return null;
        } else {
            return put1(key, CString.valueOf(entry), 2);
        }
    }

    public final CEntry put(String key, int entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.INTEGER) {
            ((CInteger) prev).value = entry;
            return null;
        } else {
            return put1(key, CInteger.valueOf(entry), 2);
        }
    }

    public final CEntry put(String key, long entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.LONG) {
            ((CLong) prev).value = entry;
            return null;
        } else {
            return put1(key, CLong.valueOf(entry), 2);
        }
    }

    public final CEntry put(String key, double entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.DOUBLE) {
            ((CDouble) prev).value = entry;
            return null;
        } else {
            return put1(key, CDouble.valueOf(entry), 2);
        }
    }

    public final CEntry put(String key, boolean entry) {
        return put1(key, CBoolean.valueOf(entry), 2);
    }



    public final CEntry putIfAbsent(String key, CEntry entry) {
        return put1(key, entry, 0);
    }

    public final String putIfAbsent(String key, String entry) {
        return put1(key, CString.valueOf(entry), 0).asString();
    }

    public final boolean putIfAbsent(String key, boolean entry) {
        return put1(key, CBoolean.valueOf(entry), 0).asBool();
    }

    public final int putIfAbsent(String key, int entry) {
        return put1(key, CInteger.valueOf(entry), 0).asInteger();
    }

    public final int putIfAbsent(String key, long entry) {
        return put1(key, CLong.valueOf(entry), 0).asInteger();
    }

    public final double putIfAbsent(String key, double entry) {
        return put1(key, CDouble.valueOf(entry), 0).asDouble();
    }

    public final CMapping getOrCreateMap(String key) {
        return put1(key, new CMapping(), 0).asMap();
    }

    public final CList getOrCreateList(String key) {
        return put1(key, new CList(), 0).asList();
    }

    // endregion
    // region GET

    public final boolean containsKey(String key) {
        return get1(key, false, null) != null;
    }

    public final boolean containsKey(String key, Type type) {
        return get(key).getType().isSimilar(type);
    }

    public final boolean getBool(String key) {
        CEntry entry = get(key);
        return entry.getType().isSimilar(Type.BOOL) && entry.asBool();
    }

    public final String getString(String key) {
        CEntry entry = get(key);
        return entry.getType().isSimilar(Type.STRING) ? entry.asString() : "";
    }

    public final int getInteger(String key) {
        CEntry entry = get(key);
        return entry.getType().isNumber() ? entry.asInteger() : 0;
    }

    public final long getLong(String key) {
        CEntry entry = get(key);
        return entry.getType().isNumber() ? entry.asLong() : 0L;
    }

    public final double getDouble(String key) {
        CEntry entry = get(key);
        return entry.getType().isNumber() ? entry.asDouble() : 0;
    }

    public final CEntry get(String key) {
        return get1(key, false, CNull.NULL);
    }

    public final CEntry getOrNull(String key) {
        return get1(key, false, null);
    }

    public final CEntry getDot(String key) {
        return get1(key, true, CNull.NULL);
    }

    // endregion

    public static final int F_DOTTED = 1, F_REPLACE = 2;

    public CEntry put1(String keys, CEntry value, int flag) {
        if (null == dot && (flag & F_DOTTED) == 0) {
            if ((flag & F_REPLACE) != 0 || !map.getOrDefault(keys, CNull.NULL).isSimilar(value)) {
                map.put(keys, value);
                return value;
            }
            return map.getOrDefault(keys, value);
        }
        CharList tmp = dot == null ? new CharList() : dot;
        tmp.clear();

        CEntry entry = this;
        int i = 0;
        do {
            i = _name(keys, tmp, i);

            Map<String, CEntry> map = entry.asMap().map;
            // noinspection all
            entry = map.getOrDefault(tmp, CNull.NULL);
            if (i == keys.length()) {
                if ((flag & F_REPLACE) != 0 || !entry.isSimilar(value)) {
                    map.put(tmp.toString(), entry = value);
                }
            } else {
                if (entry.getType() == Type.NULL) {
                    map.put(tmp.toString(), entry = new CMapping());
                }
            }
            tmp.clear();
        } while (i < keys.length());
        return entry == CNull.NULL ? value : entry;
    }

    public CEntry get1(String keys, boolean dotted, CEntry def) {
        if (keys == null) return def;
        if (null == dot && !dotted) return map.getOrDefault(keys, def);
        CharList tmp = dot == null ? new CharList() : dot;
        tmp.clear();

        CEntry entry = this;
        int i = 0;
        do {
            i = _name(keys, tmp, i);

            // 为啥这里有个i=1
            Map<String, CEntry> map = entry.asMap().map;
            // equal is content-equal
            // noinspection all
            entry = map.get(tmp);
            tmp.clear();
            if (entry == null || entry.getType() == Type.NULL) return def;
        } while (i < keys.length());
        return entry;
    }

    private static int _name(String keys, CharList tmp, int i) {
        while (i < keys.length()) {
            char c = keys.charAt(i++);
            if (c == '.') {
                int l = tmp.length();
                if (l == 0 || tmp.list[l - 1] != '\\') {
                    break;
                } else {
                    tmp.setLength(l - 1);
                }
            }
            tmp.append(c);
        }
        return i;
    }

    /**
     * @param self 优先从自身合并
     */
    public void merge(CMapping o, boolean self, boolean deep) {
        if (!deep) {
            if (!self) {
                map.putAll(o.map);
            } else {
                Map<String, CEntry> map1 = new MyHashMap<>(map);
                map.putAll(o.map);
                map.putAll(map1);
            }
        } else {
            Map<String, CEntry> map = new MyHashMap<>(o.map);
            for (Map.Entry<String, CEntry> entry : this.map.entrySet()) {
                CEntry a = entry.getValue();
                CEntry b = map.remove(entry.getKey());
                if (b != null) {
                    if (a.getType() == Type.MAP && Type.MAP.isSimilar(b.getType())) {
                        if (!self) b.asMap().merge(a.asMap(), true, true);
                        else a.asMap().merge(b.asMap(), false, true);
                    }
                    if (!self) entry.setValue(b);
                }
            }
            this.map.putAll(map);
        }
    }

    /**
     * 把不符合o定义的规则（类型，字段）的替换为o，多出的则删除
     * o : defaults
     */
    public void unmerge(CMapping o, boolean deep) {
        MyHashSet<String> names = new MyHashSet<>(this.map.keySet());
        if (!deep) {
            for (Map.Entry<String, CEntry> entry : o.map.entrySet()) {
                if (!names.remove(entry.getKey())) {
                    map.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            Map<String, CEntry> map = this.map;
            for (Map.Entry<String, CEntry> entry : o.map.entrySet()) {
                String k = entry.getKey();
                if (names.remove(k)) {
                    CEntry a = map.get(k);
                    CEntry b = entry.getValue();
                    if (!a.getType().isSimilar(b.getType())) {
                        map.put(k, b);
                    } else if (a.getType() == Type.MAP) {
                        a.asMap().unmerge(b.asMap(), true);
                    }
                } else {
                    map.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for(String key : names) {
            map.remove(key);
        }
    }

    public final void remove(String name) {
        map.remove(name);
    }

    public final void clear() {
        map.clear();
    }

    @Override
    public CMapping asMap() {
        return this;
    }

    protected String getCommentInternal(String key) { return null; }

    public boolean isCommentSupported() { return false; }

    public Map<String, String> getComments() { return Collections.emptyMap(); }

    public CMapping withComments() {
        if (getType() != Type.MAP) throw new UnsupportedOperationException();
        return new CCommMap(map);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if (!map.isEmpty()) {
            sb.append('\n');
            Iterator<Map.Entry<String, CEntry>> itr = map.entrySet().iterator();
            while (true) {
                Map.Entry<String, CEntry> entry = itr.next();

                String comment = getCommentInternal(entry.getKey());
                if (comment != null && comment.length() > 0) {
                    addComments(sb, depth, comment, "#", "\n");
                }

                for (int i = 0; i < depth; i++) sb.append(' ');

                if (!CString.NO_RAW_CHECK && CString.rawSafe(entry.getKey())) {
                    sb.append(entry.getKey());
                } else {
                    AbstLexer.addSlashes(entry.getKey(), sb).append('"');
                }
                sb.append(':').append(' ');
                entry.getValue().toYAML(sb, depth + 2);
                if (!itr.hasNext()) break;
                sb.append('\n');
            }
            return sb;
        }
        return sb.append("{}");
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        sb.append('{');
        if (!map.isEmpty()) {
            Iterator<Map.Entry<String, CEntry>> itr = map.entrySet().iterator();
            if (depth < 0) {
                while (true) {
                    Map.Entry<String, CEntry> entry = itr.next();
                    AbstLexer.addSlashes(entry.getKey(), sb.append('"')).append('"').append(':');
                    entry.getValue().toJSON(sb, -1);
                    if (!itr.hasNext()) break;
                    sb.append(',');
                }
            } else {
                sb.append('\n');
                while (true) {
                    Map.Entry<String, CEntry> entry = itr.next();

                    String comment = getCommentInternal(entry.getKey());
                    if (comment != null && comment.length() > 0) {
                        addComments(sb, depth + 4, comment, "//", "\n");
                    }

                    for (int i = 0; i < depth + 4; i++) sb.append(' ');

                    AbstLexer.addSlashes(entry.getKey(), sb.append('"')).append('"').append(':').append(' ');
                    entry.getValue().toJSON(sb, depth + 4);
                    if (!itr.hasNext()) break;
                    sb.append(",\n");
                }
                sb.append('\n');
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }
            }
        }
        return sb.append('}');
    }

    @Override
    public StringBuilder toINI(StringBuilder sb, int depth) {
        if (!map.isEmpty()) {
            Iterator<Map.Entry<String, CEntry>> itr = map.entrySet().iterator();
            if (depth == 0) {
                CEntry root = map.get("<root>");
                if (root != null) {
                    if (!(root instanceof CMapping))
                        throw new IllegalArgumentException("INI文件格式第二级必须是映射");
                    root.toINI(sb.append('\n'), 0);
                }
                while (true) {
                    Map.Entry<String, CEntry> entry = itr.next();

                    String key = entry.getKey();
                    if (key.equals("<root>")) continue;

                    String comment = getCommentInternal(entry.getKey());
                    if (comment != null && comment.length() > 0) {
                        addComments(sb, depth, comment, ";", "\n");
                    }

                    sb.append('[');
                    if (key.indexOf(']') >= 0) {
                        AbstLexer.addSlashes(entry.getKey(), sb.append('"')).append('"');
                    } else {
                        sb.append(key);
                    }
                    sb.append(']').append('\n');

                    CEntry value = entry.getValue();
                    if (!(value instanceof CMapping))
                        throw new IllegalArgumentException("INI文件格式第二级必须是映射");
                    value.toINI(sb, 1);
                    if (!itr.hasNext()) break;
                    sb.append('\n');
                }
            } else if (depth == 1) {
                while (true) {
                    Map.Entry<String, CEntry> entry = itr.next();

                    String key = entry.getKey();
                    int i = 0;
                    for (; i < key.length(); i++) {
                        if (AbstLexer.SPECIAL.contains(key.charAt(i))) {
                            i = -1;
                            break;
                        }
                    }
                    if (i < 0)
                        AbstLexer.addSlashes(entry.getKey(), sb.append('"')).append('"');
                    else
                        sb.append(key);
                    entry.getValue().toINI(sb.append(' ').append('=').append(' '), 2);
                    if (!itr.hasNext()) break;
                    sb.append('\n');
                }
            } else {
                throw new IllegalArgumentException("INI不支持两级以上的映射");
            }
        }
        return sb;
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        if (!map.isEmpty()) {
            if (chain.length() > 0 && depth < 2) {
                if (!CString.rawSafe(chain)) {
                    AbstLexer.addSlashes(chain, sb.append('[')).append("]\n");
                } else {
                    sb.append('[').append(chain).append("]\n");
                }
            }
            if (depth == 0 && map.containsKey("<root>")) map.get("<root>").toTOML(sb, 0, chain).append('\n');

            if (depth == 3) sb.append('{');
            for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                String comment = getCommentInternal(entry.getKey());
                if (comment != null && comment.length() > 0) {
                    addComments(sb, 0, comment, "#", "\n");
                }

                CEntry v = entry.getValue();
                switch (v.getType()) {
                    case MAP:
                    case OBJECT:
                        if (entry.getKey().equals("<root>") && depth == 0) continue;
                        v.toTOML(sb, depth == 3 ? 3 : 1, entry.getKey()).append('\n');
                        break;
                    case LIST:
                        v.toTOML(sb, depth == 3 ? 3 : 0, entry.getKey()).append('\n');
                        break;
                    default:
                        if (!CString.rawSafe(entry.getKey())) {
                            AbstLexer.addSlashes(chain, sb.append('"')).append('"');
                        } else {
                            sb.append(entry.getKey());
                        }
                        sb.append(" = ");
                        if (depth == 3) {
                            v.toTOML(sb, 0, "").append(", ");
                        } else {
                            v.toTOML(sb, 0, "").append('\n');
                        }
                }
            }
            sb.delete(sb.length() - (depth == 3 ? 2 : 0), sb.length());
            if (depth == 3) sb.append('}');
        }
        return sb;
    }

    @SuppressWarnings("fallthrough")
    protected static void addComments(StringBuilder sb, int depth,
            CharSequence com, CharSequence prefix, CharSequence postfix) {
        int r = 0, i = 0, prev = 0;
        while (i < com.length()) {
            switch (com.charAt(i)) {
                case '\r':
                    if (i + 1 >= com.length() || com.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        r = 1;
                        i++;
                    }
                case '\n':
                    if (prev != i) {
                        for (int j = 0; j < depth; j++) sb.append(' ');
                        sb.append(prefix).append(com, prev, i - r).append(postfix);
                    }
                    prev = i + 1;
                    r = 0;
                    break;
            }
            i++;
        }

        if (prev != i) {
            for (int j = 0; j < depth; j++) sb.append(' ');
            sb.append(prefix).append(com, prev, i).append(postfix);
        }
    }

    @Override
    public Object unwrap() {
        MyHashMap<String, Object> caster = Helpers.cast(new MyHashMap<>(map));
        for (Map.Entry<String, Object> entry : caster.entrySet()) {
            entry.setValue(((CEntry) entry.getValue()).unwrap());
        }
        return caster;
    }

    @Override
    public void toBinary(ByteList w, Structs struct) {
        if (struct != null && struct.toBinary(this, w)) {
            return;
        }
        w.put((byte) Type.MAP.ordinal()).putVarInt(map.size(), false);
        if (map.isEmpty()) return;
        for (Map.Entry<String, CEntry> entry : map.entrySet()) {
            entry.getValue().toBinary(w.putVIVIC(entry.getKey()), struct);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CMapping mapping = (CMapping) o;

        return this.map.equals(mapping.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public void serialize(StreamSerializer ser) {
        ser.valueMap();
        if (!map.isEmpty()) {
            for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                ser.key(entry.getKey());
                entry.getValue().serialize(ser);
            }
        }
        ser.pop();
    }
}
