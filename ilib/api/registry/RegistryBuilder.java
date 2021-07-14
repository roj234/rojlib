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
import roj.config.data.*;

import java.util.ArrayList;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:45
 */
public final class RegistryBuilder {
    @Deprecated
    public RegistryBuilder(int start, int end) {
        for (int i = start; i < end; i++) {
            append(String.valueOf(i));
        }
    }

    public static class Std extends IdxImpl implements Propertied<Std> {
        Std(String name, int id) {
            super(name, id);
        }

        public CEntry getProperty(String name) {
            return CNull.NULL;
        }

        public void putProperty(String name, CEntry property) {}
    }

    public static final class StdProp extends Std {
        public final MyHashMap<String, CEntry> properties = new MyHashMap<>(1, 1);

        @Override
        public CEntry getProperty(String name) {
            return properties.getOrDefault(name, CNull.NULL);
        }

        @Override
        public void putProperty(String name, CEntry property) {
            properties.put(name, property);
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

    public RegistryBuilder prop(String name, CEntry property) {
        Std currentEnum = list.get(list.size() - 1);
        if (currentEnum.getClass() != StdProp.class) {
            list.set(list.size() - 1, currentEnum = new StdProp(currentEnum.name, currentEnum.index));
        }
        currentEnum.putProperty(name, property);
        return this;
    }

    public RegistryBuilder prop(String name, int property) {
        return prop(name, CInteger.valueOf(property));
    }

    public RegistryBuilder prop(String name, double property) {
        return prop(name, CDouble.valueOf(property));
    }

    public RegistryBuilder prop(String name, boolean property) {
        return prop(name, CBoolean.valueOf(property));
    }

    public RegistryBuilder prop(String name, String property) {
        return prop(name, CString.valueOf(property));
    }

    public RegistryBuilder prop(String name, Object property) {
        return prop(name, new CObject<>(property));
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