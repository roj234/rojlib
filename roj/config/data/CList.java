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

import roj.collect.IntList;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public final class CList extends CEntry implements Iterable<CEntry> {
    final List<CEntry> list;

    public CList() {
        this(new ArrayList<>());
    }

    public CList(int size) {
        this.list = new ArrayList<>(size);
    }

    public CList(List<CEntry> list) {
        this.list = list;
    }

    public static CList of(Object... objects) {
        CList list = new CList(objects.length);
        for (Object o : objects) {
            if (o instanceof CharSequence) {
                list.add(CString.valueOf(o.toString()));
            } else if (o instanceof Number) {
                Number num = (Number) o;
                list.add(num.doubleValue() == num.longValue() ? CInteger.valueOf(num.intValue()) :
                        CDouble.valueOf(num.doubleValue()));
            } else if (o instanceof CEntry) {
                list.add((CEntry) o);
            } else if (o instanceof Boolean) {
                list.add(CBoolean.valueOf((Boolean) o));
            } else throw new ClassCastException(o.getClass() + " is unable cast. ");
        }
        return list;
    }

    public int size() {
        return list.size();
    }

    @Nonnull
    public Iterator<CEntry> iterator() {
        return list.iterator();
    }

    public CList add(@Nullable CEntry entry) {
        list.add(entry == null ? CNull.NULL : entry);
        return this;
    }

    public void add(@Nonnull String s) {
        list.add(CString.valueOf(s));
    }

    public void add(int s) {
        list.add(CInteger.valueOf(s));
    }

    public void add(double s) {
        list.add(CDouble.valueOf(s));
    }

    public void add(long s) {
        list.add(CLong.valueOf(s));
    }

    public void add(boolean b) {
        list.add(CBoolean.valueOf(b));
    }

    public void set(int index, @Nullable CEntry entry) {
        list.set(index, entry == null ? CNull.NULL : entry);
    }

    @Nonnull
    public CEntry get(int index) {
        return list.get(index);
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.LIST;
    }

    public MyHashSet<String> asStringSet() {
        MyHashSet<String> stringSet = new MyHashSet<>(list.size());
        for (CEntry entry : list) {
            try {
                String val = entry.asString();
                stringSet.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return stringSet;
    }

    public SimpleList<String> asStringList() {
        SimpleList<String> stringList = new SimpleList<>(list.size());
        for (CEntry entry : list) {
            try {
                String val = entry.asString();
                stringList.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return stringList;
    }

    public int[] asIntList() {
        IntList numberList = new IntList(list.size());
        for (CEntry entry : list) {
            try {
                int val = entry.asInteger();
                numberList.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return numberList.toArray();
    }

    @Nonnull
    @Override
    public CList asList() {
        return this;
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if (!list.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < list.size(); i++) {
                CEntry entry = list.get(i);
                for (int j = 0; j < depth; j++) {
                    sb.append(' ');
                }
                sb.append('-').append(' ');
                entry.toYAML(sb, depth + 2).append('\n');
            }
            return sb.delete(sb.length() - 1, sb.length());
        }
        return sb.append("[]");
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        sb.append('[');
        if (!list.isEmpty()) {
            if (depth > 0) sb.append('\n');
            for (CEntry entry : list) {
                for (int i = 0; i < depth + 4; i++) {
                    sb.append(' ');
                }
                entry.toJSON(sb, depth + 4).append(',');
                if (depth > 0) sb.append('\n');
            }
            if (depth > 0) {
                sb.delete(sb.length() - 2, sb.length() - 1);
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }
            } else {
                sb.delete(sb.length() - 1, sb.length());
            }
        }
        return sb.append(']');
    }

    @Override
    public Object toNudeObject() {
        List<Object> caster = Arrays.asList(new Object[list.size()]);
        for (int i = 0; i < list.size(); i++) {
            caster.set(i, list.get(i).toNudeObject());
        }
        return caster;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CList that = (CList) o;

        return Objects.equals(list, that.list);
    }

    @Override
    public int hashCode() {
        return list != null ? list.hashCode() : 0;
    }

    public void addAll(CList list) {
        this.list.addAll(list.list);
    }

    public List<CEntry> raw() {
        return list;
    }

    public void clear() {
        list.clear();
    }
}
