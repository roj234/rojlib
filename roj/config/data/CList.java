package roj.config.data;

import roj.collect.IntList;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLList.java
 */
public final class CList extends ConfEntry implements Iterable<ConfEntry> {
    final List<ConfEntry> list;

    public CList() {
        this(new ArrayList<>());
    }

    public static CList of(Object... objects) {
        CList list = new CList(objects.length);
        for (Object o : objects) {
            if (o instanceof CharSequence) {
                list.add(CString.valueOf(o.toString()));
            } else if (o instanceof Number) {
                Number num = (Number) o;
                list.add(num.doubleValue() == num.longValue() ? CInteger.valueOf(num.intValue()) : CDouble.valueOf(num.doubleValue()));
            } else if (o instanceof ConfEntry) {
                list.add((ConfEntry) o);
            } else if (o instanceof Boolean) {
                list.add(CBoolean.valueOf((Boolean) o));
            } else
                throw new ClassCastException(o.getClass() + " is unable cast. ");
        }
        return list;
    }

    public CList(int size) {
        super(Type.LIST);
        this.list = new ArrayList<>(size);
    }

    public CList(List<ConfEntry> list) {
        super(Type.LIST);
        this.list = list;
    }

    public int size() {
        return list.size();
    }

    @Nonnull
    public Iterator<ConfEntry> iterator() {
        return list.iterator();
    }

    public CList add(@Nullable ConfEntry entry) {
        list.add(entry == null ? CNull.NULL : entry);
        return this;
    }

    public void set(int index, @Nullable ConfEntry entry) {
        list.set(index, entry == null ? CNull.NULL : entry);
    }

    @Nonnull
    public ConfEntry get(int index) {
        return list.get(index);
    }

    /*@Nullable
    public Type getComponentType() {
        return list.isEmpty() ? null : list.get(0).type();
    }*/

    public MyHashSet<String> getStringSet() {
        MyHashSet<String> stringSet = new MyHashSet<>(list.size());
        for (ConfEntry entry : list) {
            try {
                String val = entry.asString();
                stringSet.add(val);
            } catch (ClassCastException ignored) {}
        }
        return stringSet;
    }

    public SimpleList<String> getStringList() {
        SimpleList<String> stringList = new SimpleList<>(list.size());
        for (ConfEntry entry : list) {
            try {
                String val = entry.asString();
                stringList.add(val);
            } catch (ClassCastException ignored) {
            }
        }
        return stringList;
    }

    public int[] getNumberList() {
        IntList numberList = new IntList(list.size());
        for (ConfEntry entry : list) {
            try {
                int val = entry.asNumber();
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
            for (ConfEntry entry : list) {
                /*if(entry.type() == Type.MAP) {
                    CMapping map = entry.asMap();
                    if(!map.map.isEmpty()) {
                        int len = sb.length();
                        map.toStringIntl(sb, depth);
                        sb.insert(len + 2, "- ");
                        continue;
                    }
                }*/

                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append('-').append(' ').append(entry.toYAML(new StringBuilder(), depth + 2)).append('\n');
            }
            return sb.delete(sb.length() - 1, sb.length());
        }
        return sb.append("[]");
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        sb.append('[');
        if (!list.isEmpty()) {
            if(depth > 0)
               sb.append('\n');
            for (ConfEntry entry : list) {
                for (int i = 0; i < depth + 4; i++) {
                    sb.append(' ');
                }
                entry.toJSON(sb, depth + 4).append(',');
                if(depth > 0)
                    sb.append('\n');
            }
            if(depth > 0) {
                sb.delete(sb.length() - 2, sb.length() - 1);
                //sb.append('\n');
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

    public List<ConfEntry> raw() {
        return list;
    }

    public void clear() {
        list.clear();
    }
}
