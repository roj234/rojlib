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

package roj.asm.struct.attr;

import roj.annotation.Internal;
import roj.asm.cst.CstUTF;
import roj.asm.struct.insn.InsnNode;
import roj.asm.type.LocalVariable;
import roj.asm.type.ParamHelper;
import roj.asm.type.SignatureHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.collect.IntBiMap;
import roj.collect.ToIntMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.struct.insn.InsnNode.validate;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class AttrLocalVars extends Attribute implements ICodeAttribute {
    public AttrLocalVars(String name) {
        super(name);
        this.list = new ArrayList<>();
    }

    public AttrLocalVars(String name, ConstantPool pool, ByteReader r, IntBiMap<InsnNode> pcCounter, int largestIndex) {
        super(name);

        this.list = readVar(pool, r, pcCounter, largestIndex, name.equals("LocalVariableTypeTable"));
    }

    private static List<LocalVariable> readVar(ConstantPool pool, ByteReader r, IntBiMap<InsnNode> pcCounter, int largestIndex, boolean generic) {
        final int len = r.readUnsignedShort();
        List<LocalVariable> list = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            int start = r.readUnsignedShort();
            int endIndex = start + r.readUnsignedShort();

            InsnNode startNode = pcCounter.get(start);
            if (startNode == null) {
                throw new NullPointerException("Couldn't found bytecode offset " + start);
            }

            InsnNode endNode = endIndex >= largestIndex ? AttrCode.METHOD_END_MARK : pcCounter.get(endIndex);
            if (endNode == null) {
                throw new NullPointerException("Couldn't found bytecode offset " + endIndex + ", method lgi=" + largestIndex);
            }

            //The given local variable must have a value at indexes into the code array in the interval [start_pc, start_pc + length)

            String name = ((CstUTF) pool.get(r)).getString();
            String desc = ((CstUTF) pool.get(r)).getString();
            list.add(new LocalVariable(r.readUnsignedShort(),
                    name,
                    generic ? SignatureHelper.parse(desc) : ParamHelper.parseField(desc),
                    startNode, endNode
            ));
        }
        return list;
    }

    public final List<LocalVariable> list;

    public String toString() {
        StringBuilder sb = new StringBuilder("Slot\tType\tName\tStart\tEnd\n");
        for (LocalVariable variable : list) {
            sb.append(variable).append('\n');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    /*
        LocalVariableTable_attribute {
            u2 attribute_name_index;
            u4 attribute_length;
            u2 local_variable_table_length;
            {   u2 start_pc;
                u2 length;
                u2 name_index;
                u2 descriptor_index;
                u2 index;
            } local_variable_table[local_variable_table_length];
        }
    */
    @Override
    @Internal
    public void toByteArray(ConstantWriter pool, ByteWriter w, ToIntMap<InsnNode> pcRev) {
        w.writeShort(list.size());
        for (LocalVariable c : list) {
            InsnNode s = validate(c.start);
            InsnNode e = validate(c.end);
            w.writeShort(pcRev.getInt(s))
                    .writeShort(pcRev.getInt(e) - pcRev.getInt(s))
                    .writeShort(pool.getUtfId(c.name))
                    .writeShort(pool.getUtfId(c.type.isGeneric() ? c.type.toGeneric() : ParamHelper.getField((Type) c.type)))
                    .writeShort(c.slot);
        }
    }

    public String toString(AttrLocalVars table) {
        StringBuilder sb = new StringBuilder("Slot\tType\tName\tStart\tEnd\n");
        int i = 0;
        for (LocalVariable variable : list) {
            if (table != null && table.list.size() > i) {
                sb.append(table.list.get(i++));
            } else {
                sb.append(variable);
            }
            sb.append('\n');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }
}