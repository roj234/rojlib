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

package roj.asm.tree.attr;

import roj.asm.cst.Constant;
import roj.asm.cst.CstRefUTF;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;
import roj.util.ByteReader;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrUTFRef extends Attribute {
    public AttrUTFRef(String name) {
        super(name);
    }

    public AttrUTFRef(String name, ByteReader r, ConstantPool pool) {
        super(name);
        Constant cst = pool.get(r);
        this.cst = ((CstRefUTF)cst);
    }

    public CstRefUTF cst;

    @Override
    protected void toByteArray1(ConstantPool pool, ByteList w) {
        w.putShort(pool.reset(cst).getIndex());
    }

    public String toString() {
        return name + ": " + cst.getValue().getString();
    }
}