/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrMethodParameters.java
 */
package roj.asm.struct.attr;

import roj.asm.cst.CstUTF;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.asm.util.FlagList;
import roj.collect.LinkedMyHashMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.Map;
import java.util.PrimitiveIterator;

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
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
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