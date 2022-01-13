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

import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.word.AbstLexer;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public class CMapping extends CEntry {
    final   Map<String, CEntry> map;
    private CharList            dot;

    public CMapping() {
        this.map = new LinkedMyHashMap<>();
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

    public final CharList dot(boolean dotMode) {
        if (dotMode == (dot == null))
            this.dot = dotMode ? new CharList() : null;
        return dot;
    }

    public final Map<String, CEntry> raw() {
        return map;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.MAP;
    }

    @Nonnull
    public final Set<String> keySet() {
        return map.keySet();
    }

    @Nonnull
    public final Set<Map.Entry<String, CEntry>> entrySet() {
        return map.entrySet();
    }

    @Nonnull
    public final Collection<CEntry> values() {
        return map.values();
    }

    public final CEntry put(@Nonnull String key, CEntry entry) {
        return putsIfAbsent(key, entry == null ? CNull.NULL : entry, 2);
    }

    public final CEntry put(@Nonnull String key, String entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.STRING) {
            if (entry == null) {
                return put(key, CNull.NULL);
            }
            ((CString) prev).value = entry;
            return null;
        } else {
            return put(key, entry == null ? CNull.NULL : new CString(entry));
        }
    }

    public final CEntry put(@Nonnull String key, int entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.INTEGER) {
            ((CInteger) prev).value = entry;
            return null;
        } else {
            return put(key, new CInteger(entry));
        }
    }

    public final CEntry put(@Nonnull String key, double entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.DOUBLE) {
            ((CDouble) prev).value = entry;
            return null;
        } else {
            return put(key, new CDouble(entry));
        }
    }

    public final CEntry put(@Nonnull String key, long entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.LONG) {
            ((CLong) prev).value = entry;
            return null;
        } else {
            return put(key, new CLong(entry));
        }
    }

    public final CEntry put(@Nonnull String key, boolean entry) {
        return put(key, CBoolean.valueOf(entry));
    }

    public final long getLong(String key) {
        CEntry entry = getDot0(key);
        return entry.getType().fits(Type.LONG) ? entry.asLong() : 0L;
    }

    public final boolean getBool(String key) {
        CEntry entry = getDot0(key);
        return entry.getType().fits(Type.BOOL) && entry.asBool();
    }

    public final String getString(String key) {
        CEntry entry = getDot0(key);
        return entry.getType().fits(Type.STRING) ? entry.asString() : "";
    }

    public final int getInteger(String key) {
        CEntry entry = getDot0(key);
        return entry.getType().isNumber() ? entry.asInteger() : 0;
    }

    public final double getDouble(String key) {
        CEntry entry = getDot0(key);
        return entry.getType().isNumber() ? entry.asDouble() : 0;
    }

    @Nonnull
    public final CEntry get(String key) {
        return getDotEntry(key, false, CNull.NULL);
    }

    @Nullable
    public final CEntry getOrNull(String key) {
        return getDotEntry(key, false, null);
    }

    @Nonnull
    public final CEntry getDot(String key) {
        return getDotEntry(key, true, CNull.NULL);
    }

    private CEntry getDot0(String key) {
        return getDotEntry(key, false, CNull.NULL);
    }

    public CEntry getDotEntry(String keys, boolean force, CEntry def) {
        if (keys == null) return def;
        if (null == dot && !force) return map.getOrDefault(keys, def);
        CharList tmp = dot == null ? new CharList() : dot;
        tmp.clear();

        CEntry entry = this;
        int i = 0;
        do {
            do {
                char c = keys.charAt(i++);
                if (c == '.') break;
                tmp.append(c);
                continue;
            } while (false);

            if (i == 1 || '\\' != tmp.charAt(tmp.length() - 1) || i == keys.length()) {
                Map<String, CEntry> map = entry.asMap().map;
                // equal is content-equal
                entry = map.get(tmp);
                tmp.clear();
                if (entry == null || entry.getType() == Type.NULL) return def;
            }
        } while (i < keys.length());
        return entry;
    }

    public CEntry putsIfAbsent(String keys, CEntry value, int force) {
        if (null == dot && (force & 1) == 0) {
            if ((force & 2) != 0 || !containsKey(keys, value)) map.put(keys, value);
            return map.getOrDefault(keys, value);
        }
        CharList tmp = dot == null ? new CharList() : dot;
        tmp.clear();

        CEntry entry = this;
        int i = 0;
        do {
            do {
                char c = keys.charAt(i++);
                if (c == '.') break;
                tmp.append(c);
                continue;
            } while (false);

            if (i == 1 || '\\' != tmp.charAt(tmp.length() - 1) || i == keys.length()) {
                Map<String, CEntry> map = entry.asMap().map;
                // equal is content-equal
                entry = map.getOrDefault(tmp, CNull.NULL);
                if (i == keys.length()) {
                    if (!entry.isSimilar(value) || (force & 2) != 0) {
                        map.put(tmp.toString(), entry = value);
                    }
                } else {
                    if (entry.getType() == Type.NULL) {
                        map.put(tmp.toString(), entry = new CMapping());
                    }
                }
                tmp.clear();
            }
        } while (i < keys.length());
        return entry == CNull.NULL ? value : entry;
    }

    public final CEntry putIfAbsent(@Nonnull String key, @Nonnull CEntry entry) {
        return putsIfAbsent(key, entry, 0);
    }

    public final String putIfAbsent(@Nonnull String key, @Nonnull String entry) {
        return putsIfAbsent(key, CString.valueOf(entry), 0).asString();
    }

    public final int putIfAbsent(@Nonnull String key, int entry) {
        return putsIfAbsent(key, CInteger.valueOf(entry), 0).asInteger();
    }

    public final double putIfAbsent(@Nonnull String key, double entry) {
        return putsIfAbsent(key, CDouble.valueOf(entry), 0).asDouble();
    }

    public final boolean putIfAbsent(@Nonnull String key, boolean entry) {
        return putsIfAbsent(key, CBoolean.valueOf(entry), 0).asBool();
    }

    public final CMapping getOrCreateMap(String key) {
        return putsIfAbsent(key, new CMapping(), 0).asMap();
    }

    public final CList getOrCreateList(String key) {
        return putsIfAbsent(key, new CList(), 0).asList();
    }

    public final boolean containsKey(@Nullable String key) {
        return getOrNull(key) != null;
    }

    public final boolean containsKey(@Nullable String key, @Nonnull Type type) {
        return get(key).getType().fits(type);
    }

    private boolean containsKey(@Nullable String key, @Nonnull CEntry entry) {
        return get(key).isSimilar(entry);
    }


    /**
     * 与o合并 警告，可能会导致list有重复对象
     * @param selfBetter 优先从自身的map/list合并
     */
    public void merge(CMapping o, boolean selfBetter, boolean deep) {
        if (!deep) {
            if (!selfBetter) {
                this.map.putAll(o.map);
            } else {
                Map<String, CEntry> map1 = new MyHashMap<>(this.map);
                this.map.putAll(o.map);
                this.map.putAll(map1);
            }
        } else {
            Map<String, CEntry> map = new MyHashMap<>(o.map);
            for (Map.Entry<String, CEntry> entry : this.map.entrySet()) {
                CEntry s_val = entry.getValue();
                CEntry t_val = map.remove(entry.getKey());
                if (t_val != null) {
                    if (s_val.getType().fits(t_val.getType())) {
                        switch (s_val.getType()) {
                            case MAP:
                                if (!selfBetter) t_val.asMap().merge(s_val.asMap(), true, true);
                                else s_val.asMap().merge(t_val.asMap(), false, true);
                                break;
                            case LIST:
                                if (!selfBetter) t_val.asList().addAll(s_val.asList());
                                else s_val.asList().addAll(t_val.asList());
                                break;
                        }
                    }
                    if (!selfBetter) entry.setValue(t_val);
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
                    CEntry s_val = map.get(k);
                    CEntry t_val = entry.getValue();
                    if (!s_val.getType().fits(t_val.getType())) {
                        map.put(k, t_val);
                    } else if (s_val.getType() == Type.MAP) {
                        s_val.asMap().unmerge(t_val.asMap(), true);
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

    public final void clear() {
        map.clear();
    }

    @Nonnull
    @Override
    public final CMapping asMap() {
        return this;
    }

    protected String getCommentInternal(String key) { return null; }

    public boolean isCommentSupported() { return false; }

    public Map<String, String> getComments() { return Collections.emptyMap(); }

    public CMapping withComments() {
        if (getType() != Type.MAP) throw new UnsupportedOperationException();
        return new CMappingCommented(map);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if (!map.isEmpty()) {
            sb.append('\n');
            for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }

                String comment = getCommentInternal(entry.getKey());
                if (comment != null && comment.length() > 0) {
                    addComments(sb.append('#'), depth, comment, "\n# ");
                    sb.delete(sb.length() - 2, sb.length());
                }

                sb.append((CString.YAMLADDITIONALCHECK && CString.rawSafe(entry.getKey())) ? entry.getKey() : addSlash(entry.getKey())).append(':').append(' ');
                entry.getValue().toYAML(sb, depth + 2).append('\n');
            }
            return sb.delete(sb.length() - 1, sb.length());
        }
        return sb.append("{}");
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        sb.append('{');
        if (!map.isEmpty()) {
            if (depth < 0) {
                for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                    sb.append('"').append(AbstLexer.addSlashes(entry.getKey())).append('"').append(':');
                    entry.getValue().toJSON(sb, -1).append(',');
                }
                sb.delete(sb.length() - 1, sb.length());
            } else {
                sb.append('\n');
                for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                    for (int i = 0; i < depth + 4; i++) {
                        sb.append(' ');
                    }

                    String comment = getCommentInternal(entry.getKey());
                    if (comment != null && comment.length() > 0) {
                        addComments(sb.append("//"), depth, comment, "\n");
                        sb.delete(sb.length() - 3, sb.length());
                    }

                    sb.append('"').append(AbstLexer.addSlashes(entry.getKey())).append('"').append(':').append(' ');
                    entry.getValue().toJSON(sb, depth + 4).append(',').append('\n');
                }
                sb.delete(sb.length() - 2, sb.length() - 1);
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
            if (depth == 0) {
                CEntry root = map.get("<root>");
                if (root != null) {
                    if (!(root instanceof CMapping))
                        throw new IllegalArgumentException("INI文件格式第二级必须是映射");
                    root.toINI(sb.append('\n'), 0);
                }
                for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                    String key = entry.getKey();
                    if (key.equals("<root>")) continue;

                    String comment = getCommentInternal(entry.getKey());
                    if (comment != null && comment.length() > 0) {
                        addComments(sb.append(";"), depth, comment, "\n; ");
                        sb.delete(sb.length() - 2, sb.length());
                    }

                    sb.append('[');
                    if (key.indexOf(']') >= 0) {
                        sb.append('"').append(AbstLexer.addSlashes(key)).append('"');
                    } else {
                        sb.append(key);
                    }
                    sb.append(']').append('\n');

                    CEntry value = entry.getValue();
                    if (!(value instanceof CMapping))
                        throw new IllegalArgumentException("INI文件格式第二级必须是映射");
                    value.toINI(sb, 1).append('\n');
                }
                sb.delete(sb.length() - 1, sb.length());
            } else if (depth == 1) {
                for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                    String key = entry.getKey();
                    int i = 0;
                    for (; i < key.length(); i++) {
                        if (AbstLexer.SPECIAL.contains(key.charAt(i))) {
                            i = -1;
                            break;
                        }
                    }
                    if (i < 0)
                        sb.append('"').append(AbstLexer.addSlashes(key)).append('"');
                    else
                        sb.append(key);
                    entry.getValue().toINI(sb.append(' ').append('=').append(' '), 2).append('\n');
                }
                sb.delete(sb.length() - 1, sb.length());
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
                    sb.append('[').append(AbstLexer.addSlashes(chain)).append("]\n");
                } else {
                    sb.append('[').append(chain).append("]\n");
                }
            }
            if (depth == 0 && map.containsKey("<root>")) map.get("<root>").toTOML(sb, 0, chain).append('\n');

            if (depth == 3) sb.append('{');
            for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                String comment = getCommentInternal(entry.getKey());
                if (comment != null && comment.length() > 0) {
                    addComments(sb.append('#'), 0, comment, "\n# ");
                    sb.delete(sb.length() - 2, sb.length());
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
                            sb.append('"').append(AbstLexer.addSlashes(entry.getKey())).append('"');
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

    protected static void addComments(StringBuilder sb, int depth, CharSequence comment, CharSequence end) {
        int r = 0, i = 0, prev = 0;
        while (i < comment.length()) {
            switch (comment.charAt(i)) {
                case '\r':
                    if (i + 1 >= comment.length() || comment.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        r = 1;
                        i++;
                    }
                case '\n':
                    for (int j = 0; j < depth; j++) sb.append(' ');
                    if (prev != i) sb.append(comment, prev, i - r);
                    sb.append(end);
                    prev = i + 1;
                    r = 0;
                    break;
            }
            i++;
        }

        for (int j = 0; j < depth; j++) sb.append(' ');
        if (prev != i) sb.append(comment, prev, i);
        sb.append(end);
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
    public void toBinary(ByteList w) {
        w.put((byte) Type.MAP.ordinal()).putVarInt(map.size(), false);
        for (Map.Entry<String, CEntry> entry : map.entrySet()) {
            entry.getValue().toBinary(w.putVarIntUTF(entry.getKey()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CMapping mapping = (CMapping) o;

        return Objects.equals(map, mapping.map);
    }

    @Override
    public int hashCode() {
        return map != null ? map.hashCode() : 0;
    }

    public final void remove(String name) {
        map.remove(name);
    }
}
