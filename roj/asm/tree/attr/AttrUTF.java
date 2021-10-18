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

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class AttrUTF extends Attribute {
    public static final String NEST_HOST = "NestHost";
    public static final String SIGNATURE = "Signature";

    public AttrUTF(String name) {
        super(name);
    }

    public AttrUTF(String name, ByteReader r, ConstantPool pool) {
        super(name);
        this.value = ((CstUTF)pool.get(r)).getString();
    }

    public String value;

    public AttrUTF(String name, String value) {
        super(name);
        this.value = value;
    }

    @Override
    protected void toByteArray1(ConstantPool pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(value));
    }

    public String toString() {
        return name + ": " + value;
    }
}