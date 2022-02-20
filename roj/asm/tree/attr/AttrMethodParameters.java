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
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.ByteReader;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrMethodParameters extends Attribute {
    public static final String NAME = "MethodParameters";

    public AttrMethodParameters() {
        super(NAME);
    }

    public AttrMethodParameters(ByteReader r, ConstantPool pool) {
        super(NAME);
        initialize(r, pool);
    }

    public final SimpleList<MethodParam> flags = new SimpleList<>();

    /*
    MethodParameters_attribute {
        u2 attribute_name_index;
        u4 attribute_length;
        u1 parameters_count;
        {   u2 name_index;
            u2 access_flags;
        } parameters[parameters_count];
    }
    */
    public void initialize(ByteReader r, ConstantPool pool) {
        short count = r.readUByte();
        for (int i = 0; i < count; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            flags.add(new MethodParam(name, r.readChar()));
        }
    }

    @Override
    protected void toByteArray1(ConstantPool pool, ByteList w) {
        w.putShort((short) flags.size());
        for (int i = 0, size = flags.size(); i < size; i++) {
            MethodParam e = flags.get(i);
            w.putShort(pool.getUtfId(e.name)).putShort(e.flag);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("MethodParameters: \n");
        for (int i = 0; i < flags.size(); i++) {
            MethodParam e = flags.get(i);
            sb.append("    Name: ").append(e.name).append("\n    Access: ");
            AccessFlag.toString(e.flag, AccessFlag.TS_PARAM, sb);
            sb.append('\n');
        }
        return sb.toString();
    }

    public static final class MethodParam {
        public String name;
        public char flag;

        public MethodParam(String name, char flag) {
            this.name = name;
            this.flag = flag;
        }
    }
}