/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: SwitchInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.util.ConstantWriter;
import roj.collect.IntMap;
import roj.util.ByteWriter;

import java.util.PrimitiveIterator;
import java.util.function.ToIntFunction;

public final class SwitchInsnNode extends InsnNode {
    public SwitchInsnNode(byte code, InsnNode def, IntMap<InsnNode> mapping) {
        super(code);
        this.def = def;
        this.mapping = mapping;
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
                return true;
        }
        return false;
    }

    public InsnNode def;
    public IntMap<InsnNode> mapping;

    ToIntFunction<InsnNode> pcRev;

    @Override
    public boolean handlePCRev(ToIntFunction<InsnNode> pcRev) {
        this.pcRev = pcRev;
        return false;
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        if(pad == -1) {
            int len = w.list.pos() + 1;
            this.pad = ((len & 3) == 0 ? 0 : 4 - (len & 3));
        }

        int vl;
        if (this.code == Opcodes.TABLESWITCH) {
            vl = 1 + pad + 4 + 8 + mapping.size() * 4;
        } else {
            vl = 1 + pad + 8 + mapping.size() * 8;
        }
        w.list.pos(w.list.pos() + vl);
    }

    @Override
    public void toByteArray(ByteWriter w) {
        if(pad == -1) {
            throw new IllegalStateException();
        }

        super.toByteArray(w);

        ToIntFunction<InsnNode> pcRev = this.pcRev;
        int self = pcRev.applyAsInt(this);

        w.list.pos(w.list.pos() + pad);
        if (this.code == Opcodes.TABLESWITCH) {
            long hl = calculateShouldUseTable();
            w.writeInt(pcRev.applyAsInt(validate(def)) - self)
                    .writeInt((int) hl).writeInt((int) (hl >>> 32));
            for (InsnNode node : mapping.values()) {
                w.writeInt(pcRev.applyAsInt(validate(node)) - self);
            }
        } else {
            w.writeInt(pcRev.applyAsInt(validate(def)) - self)
                    .writeInt(mapping.size());
            for (IntMap.Entry<InsnNode> entry : mapping.entrySet()) {
                w.writeInt(entry.getKey())
                        .writeInt(pcRev.applyAsInt(validate(entry.getValue())) - self);
            }
        }

        this.pcRev = null;
        this.pad = -1;
    }

    public int pad = -1;

    private long calculateShouldUseTable() {
        int nlabels = mapping.size();
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (PrimitiveIterator.OfInt itr = mapping.keySet().iterator(); itr.hasNext(); ) {
            int val = itr.nextInt();
            if (val > hi) hi = val;
            if (val < lo) lo = val;
        }

        return ((((long)hi) & 0xFFFFFFFFL) << 32) | (((long)lo) & 0xFFFFFFFFL);
        //long table_space_cost = 4 + 8 * ((long) hi - lo + 1); // words
        //long table_time_cost = 3; // comparisons
        //long lookup_space_cost = 8 * (long) nlabels;
        //return nlabels > 0 && table_space_cost + 3 * table_time_cost <= lookup_space_cost + 3 * (long) nlabels;

    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(" {\n");
        for (IntMap.Entry<InsnNode> entry : mapping.entrySet()) {
            sb.append("               ").append(entry.getKey()).append(" : ").append(entry.getValue()).append('\n');
        }
        return sb.append("               default: ").append(def).append("\n            }").toString();
    }
}