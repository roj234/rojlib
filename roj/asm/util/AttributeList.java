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

import roj.asm.struct.attr.Attribute;
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

    public void putByName(Attribute attribute) {
        putByName(attribute.name, attribute);
    }

    public void putByName(String name, Attribute attribute) {
        int index = -1;
        for (int i = 0; i < size; i++) {
            if (((Attribute) list[i]).name.equals(name)) {
                index = i;
                break;
            }
        }
        if (index == -1)
            add(attribute);
        else
            list[index] = attribute;
        initMap().put(name, attribute);
    }

    public Object getByName(String name) {
        return initMap().get(name);
    }

    public boolean removeByName(String name) {
        Object o = initMap().get(name);
        if (o == null)
            return false;
        return remove(o);
    }

    @Override
    public boolean add(Attribute attribute) {
        Object attr = initMap().get(attribute.name);
        if (attr != null) {
            super.set(indexOf(attr), attribute);
            return true;
        } else {
            return super.add(attribute);
        }
    }

    private Map<String, Attribute> initMap() {
        if (byName == null) {
            byName = new MyHashMap<>(size());
            for (int i = 0; i < size; i++) {
                Attribute attr = (Attribute) list[i];
                byName.put(attr.name, attr);
            }
        }
        return byName;
    }

    @Override
    protected void handleAdd(int pos, Attribute element) {
        if (byName != null)
            byName.put(element.name, element);
    }

    @Override
    protected void handleAdd(int pos, Attribute[] elements, int i, int length) {
        if (byName != null) {
            for (; i < length; i++) {
                Attribute element = elements[i];
                byName.put(element.name, element);
            }
        }
    }

    @Override
    protected void handleRemove(int pos, Attribute element) {
        if (byName != null)
            byName.remove(element.name);
    }

    @Override
    protected void handleRemove(Object[] elements, int length) {
        if (byName != null) {
            for (int i = 0; i < length; i++) {
                byName.remove(elements[i]);
            }
        }
    }

    public AttributeList() {
    }

    public AttributeList(int capacity) {
        super(capacity);
    }
}
