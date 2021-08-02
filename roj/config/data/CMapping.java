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
import roj.config.word.AbstLexer;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Config Mapping
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public class CMapping extends CEntry {
    final Map<String, CEntry> map;
    private boolean dot = false;

    public CMapping() {
        this.map = new LinkedMyHashMap<>();
    }

    public CMapping(Map<String, CEntry> map) {
        this.map = map;
    }

    public int size() {
        return map.size();
    }

    public CMapping dotMode(boolean dotMode) {
        this.dot = dotMode;
        return this;
    }

    public Map<String, CEntry> raw() {
        return map;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.MAP;
    }

    @Nonnull
    public Set<String> keySet() {
        return map.keySet();
    }

    @Nonnull
    public Set<Map.Entry<String, CEntry>> entrySet() {
        return map.entrySet();
    }

    @Nonnull
    public Collection<CEntry> values() {
        return map.values();
    }

    public CEntry put(@Nonnull String key, CEntry entry) {
        return GOC(key, entry == null ? CNull.NULL : entry, true);
    }

    public CEntry put(@Nonnull String key, String entry) {
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

    public CEntry put(@Nonnull String key, int entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.INTEGER) {
            ((CInteger) prev).value = entry;
            return null;
        } else {
            return put(key, new CInteger(entry));
        }
    }

    public CEntry put(@Nonnull String key, double entry) {
        CEntry prev = get(key);
        if (prev.getType() == Type.DOUBLE) {
            ((CDouble) prev).value = entry;
            return null;
        } else {
            return put(key, new CDouble(entry));
        }
    }

    public CEntry put(@Nonnull String key, boolean entry) {
        return put(key, CBoolean.valueOf(entry));
    }

    public long getLong(String key) {
        CEntry entry = getDottedEntry(key);
        return entry.getType().fits(Type.LONG) ? entry.asLong() : 0L;
    }

    public boolean getBool(String key) {
        CEntry entry = getDottedEntry(key);
        return entry.getType().fits(Type.BOOL) && entry.asBool();
    }

    public String getString(String key) {
        CEntry entry = getDottedEntry(key);
        return entry.getType().fits(Type.STRING) ? entry.asString() : "";
    }

    private CEntry getDottedEntry(String key) {
        return getDottedEntry(key, false, CNull.NULL);
    }

    private CEntry getDottedEntry(String keys, boolean force, CEntry defaultValue) {
        if (keys == null) {
            return defaultValue;
        }
        if (!dot && !force) return map.getOrDefault(keys, defaultValue);
        String[] arr = TextUtil.split(keys, '.');
        CEntry entry = this;
        for (int i = 0; i < arr.length; i++) {
            StringBuilder key = new StringBuilder(arr[i]);

            while (key.charAt(key.length() - 1) == '\\') {
                key.setCharAt(key.length() - 1, '.');
                key.append(arr[++i]);
            }

            entry = entry.asMap().map.get(key.toString());
            if (entry == null || entry.getType() == Type.NULL) return defaultValue;

        }
        return entry;
    }

    public int getInteger(String key) {
        CEntry entry = getDottedEntry(key);
        return entry.getType().isNumber() ? entry.asInteger() : 0;
    }

    public double getDouble(String key) {
        CEntry entry = getDottedEntry(key);
        return entry.getType().isNumber() ? entry.asDouble() : 0;
    }

    @Nullable
    public CEntry getOrNull(String key) {
        return getDottedEntry(key, false, null);
    }

    @Nonnull
    public CEntry get(String key) {
        return getDottedEntry(key, false, CNull.NULL);
    }

    @Nonnull
    public CEntry getDot(String key) {
        return getDottedEntry(key, true, CNull.NULL);
    }

    private CEntry GOC(String keys, CEntry value, boolean force) {
        if (!dot) {
            if (force || !containsKey(keys, value)) map.put(keys, value);
            return map.getOrDefault(keys, value);
        }
        List<String> arr = TextUtil.split(new ArrayList<>(), keys, '.');
        CEntry entry = this;

        for (int i = 0; i < arr.size(); i++) {
            StringBuilder key = new StringBuilder(arr.get(i));

            while (key.charAt(key.length() - 1) == '\\') {
                key.setCharAt(key.length() - 1, '.');
                key.append(arr.get(++i));
            }

            String key1 = key.toString();

            CMapping prev = entry.asMap();

            entry = prev.map.getOrDefault(key1, CNull.NULL);
            if (i == arr.size() - 1) {
                if (!entry.isSimilar(value) || force) {
                    prev.map.put(key1, entry = value);
                }
            } else {
                if (entry.getType() == Type.NULL) {
                    prev.map.put(key1, entry = new CMapping());
                }
            }

        }
        return entry == CNull.NULL ? value : entry;

    }

    public CEntry putIfAbsent(@Nonnull String key, @Nonnull CEntry entry) {
        return GOC(key, entry, false);
    }

    public String putIfAbsent(@Nonnull String key, @Nonnull String entry) {
        return GOC(key, CString.valueOf(entry), false).asString();
    }

    public int putIfAbsent(@Nonnull String key, int entry) {
        return GOC(key, CInteger.valueOf(entry), false).asInteger();
    }

    public double putIfAbsent(@Nonnull String key, double entry) {
        return GOC(key, CDouble.valueOf(entry), false).asDouble();
    }

    public boolean putIfAbsent(@Nonnull String key, boolean entry) {
        return GOC(key, CBoolean.valueOf(entry), false).asBool();
    }

    public boolean containsKey(@Nullable String key) {
        return getOrNull(key) != null;
    }

    public boolean containsKey(@Nullable String key, @Nonnull Type type) {
        return get(key).getType().fits(type);
    }

    private boolean containsKey(@Nullable String key, @Nonnull CEntry entry) {
        return get(key).isSimilar(entry);
    }

    public void merge(CMapping anotherMapping, boolean selfBetter, boolean deep) {
        if (!deep) {
            if (!selfBetter) {
                this.map.putAll(anotherMapping.map);
            } else {
                Map<String, CEntry> map1 = new HashMap<>(this.map);
                this.map.clear();
                this.map.putAll(anotherMapping.map);
                this.map.putAll(map1);
            }
        } else {
            Map<String, CEntry> map = new HashMap<>(anotherMapping.map);
            for (Map.Entry<String, CEntry> entryEntry : this.map.entrySet()) {
                CEntry entry = entryEntry.getValue();
                CEntry entry1 = map.remove(entryEntry.getKey());
                if (entry1 != null) {
                    if (entry.getType().fits(entry1.getType())) {
                        switch (entry.getType()) {
                            case MAP:
                                if (!selfBetter) entry1.asMap().merge(entry.asMap(), false, true);
                                else entry.asMap().merge(entry1.asMap(), true, true);
                                break;
                            case LIST:
                                if (!selfBetter) entry1.asList().addAll(entry.asList());
                                else entry.asList().addAll(entry1.asList());
                                break;
                        }
                    }
                    if (!selfBetter) entryEntry.setValue(entry1);
                }
            }
            this.map.putAll(map);
        }
    }

    public void clear() {
        map.clear();
    }

    @Nonnull
    @Override
    public CMapping asMap() {
        return this;
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if (!map.isEmpty()) {
            sb.append('\n');
            for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append((CString.YAMLADDITIONALCHECK && CString.yamlAdditionalCheck(entry.getKey())) ? entry.getKey() : addSlash(entry.getKey())).append(':').append(' ');
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
                    entry.getValue().toJSON(sb, -9999999).append(',');
                }
                sb.delete(sb.length() - 1, sb.length());
            } else {
                sb.append('\n');
                for (Map.Entry<String, CEntry> entry : map.entrySet()) {
                    for (int i = 0; i < depth + 4; i++) {
                        sb.append(' ');
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

    public CMapping getOrCreateMap(String key) {
        return GOC(key, new CMapping(), false).asMap();
    }

    public CList getOrCreateList(String key) {
        return GOC(key, new CList(), false).asList();
    }

    public void remove(String name) {
        map.remove(name);
    }
}
