/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrLocalVars.java
 */
package roj.asm.struct.attr;

import roj.annotation.Internal;
import roj.asm.constant.CstUTF;
import roj.asm.struct.insn.InsnNode;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.asm.util.type.LocalVariable;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.SignatureHelper;
import roj.asm.util.type.Type;
import roj.collect.IntBiMap;
import roj.collect.ToIntMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.struct.insn.InsnNode.validate;

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