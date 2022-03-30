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
package ilib.api.registry;

import ilib.api.registry.Indexable.Impl;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;

import java.util.function.IntFunction;

/**
 * @author Roj234
 * @since  2020/8/22 13:40
 */
public class Registry<T extends Indexable> implements IRegistry<T> {
    IntBiMap<T> values = new IntBiMap<>();
    MyHashMap<String, T> nameIndex = new MyHashMap<>();
    T[] arr;
    IntFunction<T[]> arrayGet;

    public Registry(IntFunction<T[]> arrayGet) {
        this.arrayGet = arrayGet;
    }

    @Override
    public T[] values() {
        if (arr == null) {
            T[] arr = this.arr = arrayGet.apply(values.size());
            for (int i = 0; i < values.size(); i++) {
                arr[i] = values.get(i);
            }
        }
        return arr;
    }

    public int appendValue(T t) {
        int i;
        this.values.put(i = values.size(), t);
        this.nameIndex.put(t.getName(), t);
        if (t instanceof Impl) ((Impl) t).index = i;
        this.arr = null;
        return i;
    }

    @Override
    public T byId(int meta) {
        return values.get(meta);
    }

    public T byName(String meta) {
        return nameIndex.get(meta);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<T> getValueClass() {
        return (Class<T>) values.get(0).getClass();
    }

    public int idFor(T t) {
        return values.getInt(t);
    }
}
