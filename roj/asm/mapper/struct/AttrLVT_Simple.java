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
import roj.asm.type.ParamHelper;
import roj.asm.type.SignatureHelper;
import roj.asm.util.ConstantPool;
import roj.asm.util.IType;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.util.ArrayList;
import java.util.List;

public final class AttrLVT_Simple {
    public AttrLVT_Simple(ConstantPool pool, ByteReader r, boolean generic) {
        this.list = readVar(pool, r, generic);
    }

    private static List<V> readVar(ConstantPool pool, ByteReader r, boolean generic) {
        final int len = r.readUnsignedShort();
        List<V> list = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            r.index += 4;
            int clo = r.index;
            CstUTF name = ((CstUTF) pool.get(r));
            CstUTF desc = ((CstUTF) pool.get(r));
            IType type = generic ? SignatureHelper.parse(desc.getString()) : ParamHelper.parseField(desc.getString());
            V sv = new V();
            sv.nameId = clo;
            sv.name = name;
            sv.refType = desc;
            sv.type = type;
            sv.bl = r.getBytes();
            sv.slot = r.readUnsignedShort();
            list.add(sv);
        }
        return list;
    }

    public final List<V> list;

    public static class V {
        public CstUTF name;
        public CstUTF refType;
        public IType type;
        public ByteList bl;
        public int slot, nameId;
    }
}