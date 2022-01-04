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

import roj.asm.tree.attr.Attribute;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.util.ByteList;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/12 0:23
 */
public interface MoFNode {
    void toByteArray(ConstantPool pool, ByteList w);

    String name();

    String rawDesc();

    default List<Type> parameters() {
        return ParamHelper.parseMethod(rawDesc());
    }

    FlagList accessFlag();

    default char accessFlag2() {
        return accessFlag().flag;
    }

    default Attribute attrByName(String name) {
        return (Attribute) attributes().getByName(name);
    }

    default AttributeList attributes() {
        throw new UnsupportedOperationException();
    }

    int type();
}
