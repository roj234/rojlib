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

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.FlagList;

import java.util.List;

/**
 * {@link roj.asm.tree.ConstantData}中的简单方法, 不解析{@link Attribute}
 *
 * @author Roj234
 * @version 2.0
 * @since 2021/6/18 9:51
 */
public final class MethodSimple extends SimpleComponent implements MethodNode {
    public MethodSimple(int accesses, CstUTF name, CstUTF typeName) {
        super((char) accesses, name, typeName);
    }
    public MethodSimple(FlagList accesses, CstUTF name, CstUTF typeName) {
        super(accesses, name, typeName);
    }

    String     owner;
    List<Type> params;

    @Override
    public String ownerClass() {
        return owner;
    }

    @Override
    public List<Type> parameters() {
        if (params == null) {
            params = ParamHelper.parseMethod(this.type.getString());
            params.remove(params.size() - 1);
        }
        return params;
    }

    @Override
    public Type getReturnType() {
        return ParamHelper.getReturn(type.getString());
    }

    @Internal
    public void cn(String owner) {
        this.owner = owner;
    }

    @Override
    public int type() {
        return 3;
    }
}