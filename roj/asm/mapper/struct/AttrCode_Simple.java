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
package roj.asm.mapper.struct;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.util.ByteReader;

public class AttrCode_Simple {
    public AttrCode_Simple(ByteReader list, ConstantPool pool) {
        initialize(pool, list);
    }

    public AttrLVT_Simple lvt, lvtt;

    public void initialize(ConstantPool pool, ByteReader r) {
        r.index += 4; // stack size
        int bl = r.readInt();
        r.index += bl; // code

        int len = r.readUnsignedShort(); // exception
        r.index += 8 * len;

        len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            int end = r.readInt() + r.index;
            switch (name) {
                case "LocalVariableTable":
                    lvt = new AttrLVT_Simple(pool, r, false);
                    break;
                case "LocalVariableTypeTable":
                    lvtt = new AttrLVT_Simple(pool, r, true);
                    break;
            }
            r.index = end;
        }
    }
}