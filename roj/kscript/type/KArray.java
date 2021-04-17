package roj.kscript.type;

import roj.collect.SimpleList;
import roj.kscript.KConstants;
import roj.kscript.api.IGettable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLList.java
 */
public final class KArray extends KBase implements IArray {
    final List<KType> list;

    public KArray() {
        this(new SimpleList<>());
    }

    public KArray(List<KType> list) {
        super(Type.ARRAY);
        this.list = list;
    }

    public KArray(int cap) {
        this(new SimpleList<>(cap));
    }

    @Override
    public void put(@Nonnull String key, KType entry) {
        try {
            int i = Integer.parseInt(key);
            set(i, entry);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Non-number index", e);
        }
    }

    @Override
    public boolean isInstanceOf(IGettable map) {
        return map instanceof IArray;
    }

    @Override
    public IGettable getPrototype() {
        return KConstants.ARRAY;
    }

    @Override
    public KType getOr(String id, KType kb) {
        try {
            int i = Integer.parseInt(id);
            return get(i);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return kb;
        }
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public Map<String, KType> getInternalMap() {
        return null;
    }

    @Override
    @Nonnull
    public Iterator<KType> iterator() {
        return list.iterator();
    }

    @Override
    public IArray add(@Nullable KType entry) {
        list.add(entry == null ? KNull.NULL : entry);
        return this;
    }

    @Override
    public void set(int index, @Nullable KType entry) {
        if (list.size() <= index) {
            if (list instanceof SimpleList) {
                SimpleList<KType> list = ((SimpleList<KType>) this.list);
                list.ensureCapacity(index + 1);
                Arrays.fill(list.getRawArray(), list.size(), index + 1, KUndefined.UNDEFINED);
                list.setSize(index + 1);
            } else {
                int i = index - list.size();
                while (i-- >= 0) {
                    list.add(KUndefined.UNDEFINED);
                }
            }
        }
        list.set(index, entry == null ? KNull.NULL : entry);
    }

    @Override
    @Nonnull
    public KType get(int index) {
        return index >= list.size() ? KUndefined.UNDEFINED : list.get(index);
    }

    @Nonnull
    @Override
    public KArray asArray() {
        return this;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        sb.append('[');
        if (!list.isEmpty()) {
            for (KType entry : list) {
                if (entry == null)
                    sb.append("null, ");
                else
                    entry.toString0(sb, depth).append(',').append(' ');
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(']');
    }

    @Override
    public KType copy() {
        return new KArray(new SimpleList<>(this.list));
    }

    @Override
    public boolean canCastTo(Type type) {
        return type == Type.ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KArray that = (KArray) o;

        return Objects.equals(list, that.list);
    }

    @Override
    public int hashCode() {
        return list != null ? list.hashCode() : 0;
    }

    @Override
    public void addAll(IArray list) {
        this.list.addAll(list.getRawList());
    }

    @Override
    public List<KType> getRawList() {
        return list;
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public boolean equalsTo(KType b) {
        List<KType> types = list;
        List<KType> types1 = b.asArray().getRawList();

        if (types.size() != types1.size())
            return false;

        if (types.isEmpty())
            return true;

        Iterator<KType> itra = types.iterator();
        Iterator<KType> itrb = types1.iterator();
        while (itra.hasNext()) {
            final KType next = itra.next();
            final KType next1 = itrb.next();
            if (next == null) {
                if (next1 != null)
                    return false;
            } else if (next.getType() != next1.getType() || !next1.equalsTo(next))
                return false;
        }
        return true;
    }

}
