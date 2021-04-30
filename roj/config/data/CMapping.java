package roj.config.data;

import com.google.common.annotations.Beta;
import roj.collect.LinkedMyHashMap;
import roj.config.word.AbstLexer;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: YAMLMapping.java
 */
public class CMapping extends ConfEntry {
    final Map<String, ConfEntry> map;

    public CMapping() {
        super(Type.MAP);
        this.map = new LinkedMyHashMap<>();
    }

    public CMapping(Map<String, ConfEntry> map) {
        super(Type.MAP);
        this.map = map;
    }

    CMapping(Type type, Map<String, ConfEntry> map) {
        super(type);
        this.map = map;
    }

    public int size() {
        return map.size();
    }

    private boolean dotMode = false;

    public CMapping dotMode(boolean dotMode) {
        this.dotMode = dotMode;
        return this;
    }

    @Nonnull
    public Set<String> keySet() {
        return map.keySet();
    }

    @Nonnull
    public Set<Map.Entry<String, ConfEntry>> entrySet() {
        return map.entrySet();
    }

    @Nonnull
    public Collection<ConfEntry> values() {
        return map.values();
    }

    public ConfEntry put(@Nonnull String key, ConfEntry entry) {
        return GOC(key, entry == null ? CNull.NULL : entry, true);
    }

    public ConfEntry put(@Nonnull String key, String entry) {
        ConfEntry prev = get(key);
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

    public ConfEntry put(@Nonnull String key, int entry) {
        ConfEntry prev = get(key);
        if (prev.getType() == Type.NUMBER) {
            ((CInteger) prev).value = entry;
            return null;
        } else {
            return put(key, new CInteger(entry));
        }
    }

    public ConfEntry put(@Nonnull String key, double entry) {
        ConfEntry prev = get(key);
        if (prev.getType() == Type.DOUBLE) {
            ((CDouble) prev).value = entry;
            return null;
        } else {
            return put(key, new CDouble(entry));
        }
    }

    public ConfEntry put(@Nonnull String key, boolean entry) {
        return put(key, CBoolean.valueOf(entry));
    }

    public boolean getBoolean(String key) {
        ConfEntry entry = getDottedEntry(key);
        return entry.getType().canFit(Type.BOOL) && entry.asBoolean();
    }

    public String getString(String key) {
        ConfEntry entry = getDottedEntry(key);
        return entry.getType().canFit(Type.STRING) ? entry.asString() : "";
    }

    private ConfEntry getDottedEntry(String key) {
        return getDottedEntry(key, false, CNull.NULL);
    }

    private ConfEntry getDottedEntry(String keys, boolean force, ConfEntry defaultValue) {
        if (keys == null) {
            return defaultValue;
        }
        if (!dotMode && !force) return map.getOrDefault(keys, defaultValue);
        String[] arr = TextUtil.splitString(keys, '.');
        ConfEntry entry = this;
        for (int i = 0; i < arr.length; i++) {
            StringBuilder key = new StringBuilder(arr[i]);

            while (key.charAt(key.length() - 1) == '\\') {
                key.setCharAt(key.length() - 1, '.');
                key.append(arr[++i]);
            }

            entry = entry.asMap().map.get(key.toString());
            if (entry == null || entry.getType() == Type.NULL)
                return defaultValue;

        }
        return entry;
    }

    public int getNumber(String key) {
        ConfEntry entry = getDottedEntry(key);
        return entry.getType().isNumber() ? entry.asNumber() : 0;
    }

    public double getDouble(String key) {
        ConfEntry entry = getDottedEntry(key);
        return entry.getType().isNumber() ? entry.asDouble() : 0;
    }

    @Nullable
    public ConfEntry getOrNull(String key) {
        return getDottedEntry(key, false, null);
    }

    @Nonnull
    public ConfEntry get(String key) {
        return getDottedEntry(key, false, CNull.NULL);
    }

    @Nonnull
    public ConfEntry getDot(String key) {
        return getDottedEntry(key, true, CNull.NULL);
    }

    private ConfEntry GOC(String keys, ConfEntry value, boolean force) {
        if (!dotMode) {
            if (force || !containsKey(keys, value))
                map.put(keys, value);
            return map.getOrDefault(keys, value);
        }
        String[] arr = TextUtil.splitString(keys, '.');
        ConfEntry entry = this;

        for (int i = 0; i < arr.length; i++) {
            StringBuilder key = new StringBuilder(arr[i]);

            while (key.charAt(key.length() - 1) == '\\') {
                key.setCharAt(key.length() - 1, '.');
                key.append(arr[++i]);
            }

            String key1 = key.toString();

            CMapping previous = entry.asMap();

            entry = previous.map.getOrDefault(key1, CNull.NULL);
            if (i == arr.length - 1) {
                if (!entry.isSimilar(value) || force) {
                    previous.map.put(key1, entry = value);
                }
            } else {
                if (entry.getType() == Type.NULL) {
                    previous.map.put(key1, entry = new CMapping());
                }
            }

        }
        return entry == CNull.NULL ? value : entry;

    }

    public ConfEntry putIfAbsent(@Nonnull String key, @Nonnull ConfEntry entry) {
        return GOC(key, entry, false);
    }

    public String putIfAbsent(@Nonnull String key, @Nonnull String entry) {
        return GOC(key, CString.valueOf(entry), false).asString();
    }

    public int putIfAbsent(@Nonnull String key, int entry) {
        return GOC(key, CInteger.valueOf(entry), false).asNumber();
    }

    public double putIfAbsent(@Nonnull String key, double entry) {
        return GOC(key, CDouble.valueOf(entry), false).asDouble();
    }

    public boolean putIfAbsent(@Nonnull String key, boolean entry) {
        return GOC(key, CBoolean.valueOf(entry), false).asBoolean();
    }

    public boolean containsKey(@Nullable String key) {
        return getOrNull(key) != null;
    }

    public boolean containsKey(@Nullable String key, @Nonnull Type type) {
        return get(key).getType().canFit(type);
    }

    private boolean containsKey(@Nullable String key, @Nonnull ConfEntry entry) {
        return get(key).isSimilar(entry);
    }

    @Beta
    public void merge(CMapping anotherMapping, boolean selfBetter, boolean deep) {
        if (!deep) {
            if (!selfBetter) {
                this.map.putAll(anotherMapping.map);
            } else {
                Map<String, ConfEntry> map1 = new HashMap<>(this.map);
                this.map.clear();
                this.map.putAll(anotherMapping.map);
                this.map.putAll(map1);
            }
        } else {
            Map<String, ConfEntry> map = new HashMap<>(anotherMapping.map);
            for (Map.Entry<String, ConfEntry> entryEntry : this.map.entrySet()) {
                ConfEntry entry = entryEntry.getValue();
                ConfEntry entry1 = map.remove(entryEntry.getKey());
                if (entry1 != null) {
                    if (entry.getType().canFit(entry1.getType())) {
                        switch (entry.getType()) {
                            case MAP:
                                if (!selfBetter)
                                    entry1.asMap().merge(entry.asMap(), false, true);
                                else
                                    entry.asMap().merge(entry1.asMap(), true, true);
                                break;
                            case LIST:
                                if (!selfBetter)
                                    entry1.asList().addAll(entry.asList());
                                else
                                    entry.asList().addAll(entry1.asList());
                                break;
                        }
                    }
                    if (!selfBetter)
                        entryEntry.setValue(entry1);
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
            for (Map.Entry<String, ConfEntry> entry : map.entrySet()) {
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append(addSlash(entry.getKey())).append(':').append(' ');
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
            if(depth < 0) {
                //int i = 0;
                for (Map.Entry<String, ConfEntry> entry : map.entrySet()) {
                    sb.append('"').append(AbstLexer.addSlashes(entry.getKey())).append('"').append(':');
                    //if(i++ > map.size())
                    //    System.out.println(entry.getKey());
                    entry.getValue().toJSON(sb, -9999999).append(',');
                }
                sb.delete(sb.length() - 1, sb.length());
            } else {
                sb.append('\n');
                for (Map.Entry<String, ConfEntry> entry : map.entrySet()) {
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

    public void remove(String name) {
        map.remove(name);
    }
}
