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

import roj.collect.MyHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/2 23:45
 */
public final class RegistryBuilder {
    @Deprecated
    public RegistryBuilder(int start, int end) {
        for (int i = start; i < end; i++) {
            append(String.valueOf(i));
        }
    }

    public static class Std extends Indexable.Impl implements Propertied<Std> {
        Std(String name, int id) {
            super(name, id);
        }

        public Object prop(String name) {
            return null;
        }

        public void prop(String name, Object property) {}
    }

    public static final class StdProp extends Std {
        public final MyHashMap<String, Object> prop = new MyHashMap<>(1, 1);

        @Override
        public Object prop(String name) {
            return prop.get(name);
        }

        @Override
        public void prop(String name, Object property) {
            prop.put(name, property);
        }

        StdProp(String name, int index) {
            super(name, index);
        }
    }

    private final List<Std> list = new ArrayList<>();
    private int index = 0;

    public RegistryBuilder() {
    }

    public RegistryBuilder(String... names) {
        addAll(names);
    }

    public RegistryBuilder append(String name) {
        list.add(new Std(name, index++));
        return this;
    }

    public RegistryBuilder prop(String name, Object property) {
        Std std = list.get(list.size() - 1);
        if (std.getClass() != StdProp.class) {
            list.set(list.size() - 1, std = new StdProp(std.name, std.index));
        }
        std.prop(name, property);
        return this;
    }

    public RegistryBuilder addAll(String... list) {
        for (String s : list)
            append(s);
        return this;
    }

    public Std[] values() {
        return this.list.toArray(new Std[index]);
    }

    public IRegistry<Std> build() {
        return RegistrySimple.from(values());
    }
}