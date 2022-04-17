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
package roj.asm.tree;

import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

import javax.annotation.Nullable;

/**
 * 简单组件基类
 *
 * @author Roj234
 * @version 1.1
 * @since 2021/5/29 17:16
 */
public abstract class SimpleComponent implements MoFNode {
    SimpleComponent(int accesses, CstUTF name, CstUTF type) {
        this.accesses = (char) accesses;
        this.name = name;
        this.type = type;
    }

    public CstUTF name, type;
    public char accesses;

    @Override
    public String name() {
        return name.getString();
    }

    @Override
    public String rawDesc() {
        return type.getString();
    }

    @Override
    public void accessFlag(int flag) {
        this.accesses = (char) flag;
    }

    @Override
    public char accessFlag() {
        return accesses;
    }

    private AttributeList attributes;

    public void toByteArray(ConstantPool pool, ByteList w) {
        w.putShort(accesses).putShort(pool.reset(name).getIndex()).putShort(pool.reset(type).getIndex());
        if (attributes == null) {
            w.putShort(0);
            return;
        }

        w.putShort(attributes.size());
        for (int i = 0; i < attributes.size(); i++) {
           attributes.get(i).toByteArray(pool, w);
        }
    }

    public Attribute attrByName(String name) {
        return attributes == null ? null : (Attribute) attributes.getByName(name);
    }

    @Override
    public AttributeList attributes() {
        return attributes == null ? attributes = new AttributeList() : attributes;
    }

    @Nullable
    @Override
    public AttributeList attributesNullable() {
        return attributes;
    }

    @Override
    public String toString() {
        return name.getString() + ' ' + type.getString();
    }
}
