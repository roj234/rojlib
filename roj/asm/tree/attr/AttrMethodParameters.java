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
import roj.asm.util.FlagList;
import roj.collect.LinkedMyHashMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.Map;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
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

    public final Map<String, FlagList> flags = new LinkedMyHashMap<>();

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
            FlagList list = AccessFlag.of(r.readShort());
            flags.put(name, list);
        }
    }

    @Override
    protected void toByteArray1(ConstantPool pool, ByteWriter w) {
        w.writeShort((short) flags.size());
        for (Map.Entry<String, FlagList> e : flags.entrySet()) {
            w.writeShort(pool.getUtfId(e.getKey())).writeShort(e.getValue().flag);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("MethodParameters: \n");
        for (Map.Entry<String, FlagList> e : flags.entrySet()) {
            sb.append("            Name: ").append(e.getKey()).append("\n         Access: ");
            for (PrimitiveIterator.OfInt itr = e.getValue().iterator(); itr.hasNext(); ) {
                sb.append(AccessFlag.byIdParameter(itr.nextInt())).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}