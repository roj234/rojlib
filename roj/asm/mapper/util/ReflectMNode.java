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
package roj.asm.mapper.util;

import roj.asm.tree.MoFNode;
import roj.asm.type.ParamHelper;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.util.ByteWriter;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Java Reflection Method.MoFNode
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/27 13:49
 */
public final class ReflectMNode implements MoFNode {
    private final Method method;

    public ReflectMNode(Method method) {this.method = method;}

    @Override
    public void toByteArray(ConstantPool pool, ByteWriter w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        return method.getName();
    }

    @Override
    public String rawDesc() {
        return ParamHelper.classDescriptors(method.getParameterTypes(), method.getReturnType());
    }

    @Override
    public FlagList accessFlag() {
        return new FlagList(method.getModifiers());
    }

    @Override
    public int type() {
        return 10;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReflectMNode that = (ReflectMNode) o;

        return method.getName().equals(that.method.getName()) && Arrays.equals(method.getParameterTypes(), that.method.getParameterTypes());
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }
}
