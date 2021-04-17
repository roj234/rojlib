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
package roj.asm.mapper.util;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;

import java.util.Collection;
import java.util.Iterator;

/**
 * Mapper List
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/11 14:59
 */
public final class MapperList extends SimpleList<String> {
    private final ToIntMap<String> indexer;

    public MapperList() {
        this(16);
    }

    public MapperList(int size) {
        ensureCapacity(size);
        indexer = new ToIntMap<>(size);
    }

    @Override
    public boolean remove(Object o) {
        if(!indexer.isEmpty())
            throw new UnsupportedOperationException();
        return super.remove(o);
    }

    @Override
    public int indexOf(Object o) {
        return indexer.getOrDefault(o, -1);
    }

    @Override
    public int lastIndexOf(Object o) {
        return indexer.getOrDefault(o, -1);
    }

    @Override
    public boolean contains(Object e) {
        return indexer.containsKey(e);
    }

    @Override
    public void clear() {
        if(!indexer.isEmpty())
            throw new UnsupportedOperationException();
        super.clear();
    }

    @Override
    public boolean addAll(int index, Collection<? extends String> collection) {
        if (collection.isEmpty()) return false;

        ensureCapacity(size + collection.size());
        if (size != index && size > 0)
            System.arraycopy(list, index, list, index + collection.size(), size - index);

        Iterator<? extends String> it = collection.iterator();
        for (int j = index; j < index + collection.size(); j++) {
            String next = it.next();
            if(indexOf(next) == -1)
                list[j] = next;
        }
        size += collection.size();
        return true;
    }

    public void _init_() {
        preClean();
        trimToSize();
    }

    public void preClean() {
        indexer.clear();
        for (int i = 0; i < size; i++) {
            Integer orig = indexer.putInt((String) list[i], i);
            if(orig != null) {
                super.remove((int) orig);
            } else if (list[i] == null) {
                size = i;
                break;
            } else {
                continue;
            }
            i = 0;
            indexer.clear();
        }
    }
}
