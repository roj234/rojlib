/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrLineNumber.java
 */
package roj.asm.struct.attr;

import roj.asm.struct.insn.InsnNode;
import roj.asm.util.ConstantWriter;
import roj.collect.IntBiMap;
import roj.collect.ToIntMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import static roj.asm.struct.insn.InsnNode.validate;

public final class AttrLineNumber extends Attribute implements ICodeAttribute {
    public ToIntMap<InsnNode> map;

    public AttrLineNumber(ByteReader r, IntBiMap<InsnNode> pcCounter) {
        super("LineNumberTable");

        final int tableLen = r.readUnsignedShort();

        ToIntMap<InsnNode> map = this.map = new ToIntMap<>(tableLen);
        for (int i = 0; i < tableLen; i++) {
            int index = r.readUnsignedShort();
            InsnNode node = pcCounter.get(index);
            if (node == null)
                throw new NullPointerException("Couldn't found bytecode offset for line number table: " + index);
            map.put(node, r.readUnsignedShort());
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("LineNumberTable:   Node <=======> Line\n");
        for (ToIntMap.Entry<InsnNode> entry : map.selfEntrySet()) {
            sb.append("                  ").append(entry.getKey()).append(" = ").append(entry.getInt()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void toByteArray(ConstantWriter pool, ByteWriter w, ToIntMap<InsnNode> pcRev) {
        w.writeShort(map.size());
        for (ToIntMap.Entry<InsnNode> entry : map.selfEntrySet()) {
            w.writeShort(pcRev.getInt(validate(entry.getKey())))
                    .writeShort(entry.getInt());
        }
    }
}