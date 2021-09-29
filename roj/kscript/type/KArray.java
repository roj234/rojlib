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
package roj.kscript.type;

import roj.collect.SimpleList;
import roj.kscript.Constants;
import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.util.JavaException;
import roj.kscript.vm.KScriptVM;
import roj.math.MathUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/16 23:19
 */
public final class KArray extends KBase implements roj.kscript.api.IArray {
    final List<KType> list;

    public KArray() {
        this(new SimpleList<>());
    }

    public KArray(List<KType> list) {
        this.list = list;
    }

    @Override
    public Type getType() {
        return Type.ARRAY;
    }

    public KArray(int cap) {
        this(new SimpleList<>(cap));
    }

    @Override
    public void put(@Nonnull String key, KType entry) {
        int[] arr = KScriptVM.retainNumParseTmp(10);
        if(!MathUtils.parseIntErrorable(key, arr))
            throw new JavaException("无效索引 " + key);
        set(arr[0], entry);
    }

    @Override
    public boolean isInstanceOf(IObject obj) {
        return obj instanceof roj.kscript.api.IArray;
    }

    @Override
    public IObject getProto() {
        return Constants.ARRAY;
    }

    @Override
    public KType getOr(String key, KType def) {
        int[] arr = KScriptVM.retainNumParseTmp(10);
        return MathUtils.parseIntErrorable(key, arr) ? get(arr[0]) : def;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    @Nonnull
    public Iterator<KType> iterator() {
        return list.iterator();
    }

    @Override
    public void add(@Nullable KType entry) {
        list.add(entry == null ? KUndefined.UNDEFINED : entry);
    }

    @Override
    public void set(int index, @Nullable KType entry) {
        if (list.size() <= index) {
            if (list instanceof SimpleList) {
                SimpleList<KType> list = ((SimpleList<KType>) this.list);
                list.ensureCapacity(index + 1);
                Arrays.fill(list.getRawArray(), list.size(), index + 1, KUndefined.UNDEFINED);
                list.i_setSize(index + 1);
            } else {
                int i = index - list.size();
                while (i-- >= 0) {
                    list.add(KUndefined.UNDEFINED);
                }
            }
        }
        list.set(index, entry == null ? KUndefined.UNDEFINED : entry);
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
    public void copyFrom(KType type) {
        final List<KType> list1 = type.asArray().getInternal();
        if(list.getClass() == list1.getClass() && list.getClass() == SimpleList.class) {
            SimpleList<KType> list = (SimpleList<KType>) this.list;
            Object[] arr = list.getRawArray();
            if(arr.length >= list1.size()) {
                int selfSize = list.size();
                System.arraycopy(((SimpleList<KType>) list1).getRawArray(), 0, arr, 0, list1.size());
                if(selfSize > list1.size()) {
                    Arrays.fill(arr, list1.size(), selfSize, null);
                }
            }
        }
        this.list.clear();
        this.list.addAll(list1);
    }

    @Override
    public boolean canCastTo(Type type) {
        return type == Type.ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return ((KArray) o).list == list;
    }

    @Override
    public int hashCode() {
        return list != null ? list.hashCode() : 0;
    }

    @Override
    public void addAll(IArray list) {
        this.list.addAll(list.getInternal());
    }

    @Override
    public List<KType> getInternal() {
        return list;
    }

    @Override
    public void clear() {
        list.clear();
    }

    //@Override
    public boolean __int_equals__(KType arr) {
        List<KType> as = list;
        List<KType> bs = arr.asArray().getInternal();

        if (as.size() != bs.size())
            return false;

        for (int i = 0; i < as.size(); i++) {
            KType a = as.get(i);
            KType b = bs.get(i);
            if(a != b) {
                if (a == null || a.getType() != b.getType() || !a.equalsTo(b)) return false;
            }
        }
        return true;
    }
}
