/*
 * This file is a part of MoreItems
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
package roj.asm.misc;

import roj.asm.Parser;
import roj.asm.tree.MoFNode;
import roj.asm.type.ParamHelper;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

import java.lang.reflect.Field;

/**
 * Java Reflection Field.MoFNode
 *
 * @author Roj233
 * @since 2022/1/11 2:13
 */
public final class ReflectFNode implements MoFNode {
    private final Field field;

    public ReflectFNode(Field field) {this.field = field;}

    @Override
    public void toByteArray(ConstantPool pool, ByteList w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        return field.getName();
    }

    @Override
    public String rawDesc() {
        return ParamHelper.class2asm(field.getType());
    }

    @Override
    public char accessFlag() {
        return (char) field.getModifiers();
    }

    @Override
    public int type() {
        return Parser.FTYPE_REFLECT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReflectFNode that = (ReflectFNode) o;

        return field.getName().equals(that.field.getName());
    }

    @Override
    public int hashCode() {
        return field.hashCode();
    }
}
