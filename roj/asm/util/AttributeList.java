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
package roj.asm.util;

import roj.asm.tree.attr.Attribute;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/25 16:50
 */
public final class AttributeList extends SimpleList<Attribute> {
    private Map<String, Attribute> byName;

    public AttributeList(AttributeList other) {
        super(other);
    }

    public void putByName(Attribute attr) {
        int index = -1;
        for (int i = 0; i < size; i++) {
            if (((Attribute) list[i]).name.equals(attr.name)) {
                index = i;
                break;
            }
        }
        if (index == -1)
            add(attr);
        else
            list[index] = attr;
        if (byName != null)
            byName.put(attr.name, attr);
    }

    public Object getByName(String name) {
        if (byName == null) {
            byName = new MyHashMap<>(size);
            for (int i = 0; i < size; i++) {
                Attribute attr = (Attribute) list[i];
                byName.put(attr.name, attr);
            }
            return byName.get(name);
        }
        Object o = byName.get(name);
        if (o == null) {
            for (int i = 0; i < size; i++) {
                if (((Attribute) list[i]).name.equals(name)) {
                    byName.put(name, (Attribute) list[i]);
                    return list[i];
                }
            }
        }
        return o;
    }

    public boolean removeByName(String name) {
        for (int i = 0; i < size; i++) {
            if (((Attribute) list[i]).name.equals(name)) {
                remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public Attribute remove(int index) {
        Attribute attr = super.remove(index);
        if (byName != null)
            byName.remove(attr.name);
        return attr;
    }

    @Override
    public boolean add(Attribute attribute) {
        if (byName == null)
            return super.add(attribute);
        Attribute a1 = byName.put(attribute.name, attribute);
        if (a1 != null) {
            super.set(indexOfAddress(a1), attribute);
            return true;
        } else {
            return super.add(attribute);
        }
    }

    public AttributeList() {
    }

    public AttributeList(int capacity) {
        super(capacity);
    }
}
