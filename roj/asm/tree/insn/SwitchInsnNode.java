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

package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.util.ConstantWriter;
import roj.collect.IntMap;
import roj.collect.LinkedIntMap;
import roj.util.ByteWriter;

import java.util.PrimitiveIterator;
import java.util.function.ToIntFunction;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 1:55
 */
public final class SwitchInsnNode extends InsnNode {
    public SwitchInsnNode(byte code) {
        super(code);
        this.switcher = new LinkedIntMap<>();
    }

    public SwitchInsnNode(byte code, InsnNode def, LinkedIntMap<InsnNode> switcher) {
        super(code);
        this.def = def;
        this.switcher = switcher;
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

    @Override
    public int nodeType() {
        return code == Opcodes.TABLESWITCH ? T_TABLESWITCH : T_LOOKUPSWITCH;
    }

    public InsnNode         def;
    public IntMap<InsnNode> switcher;

    private ToIntFunction<InsnNode> pcRev;

    @Override
    public boolean handlePCRev(ToIntFunction<InsnNode> pcRev) {
        this.pcRev = pcRev;
        return false;
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        if(pad == -1) {
            int len = w.list.pos() + 1;
            this.pad = (byte) ((len & 3) == 0 ? 0 : 4 - (len & 3));
        }

        int vl;
        if (this.code == Opcodes.TABLESWITCH) {
            vl = 1 + pad + 4 + 8 + switcher.size() * 4;
        } else {
            vl = 1 + pad + 8 + switcher.size() * 8;
        }
        w.list.pos(w.list.pos() + vl);
    }

    @Override
    public void toByteArray(ByteWriter w) {
        if(pad == -1) {
            throw new IllegalStateException();
        }

        ToIntFunction<InsnNode> pcRev = this.pcRev;
        int self = pcRev.applyAsInt(this);

        w.writeByte(code).list.pos(w.list.pos() + pad);
        if (this.code == Opcodes.TABLESWITCH) {
            long hl = calculateShouldUseTable();
            w.writeInt(pcRev.applyAsInt(validate(def)) - self)
                    .writeInt((int) hl).writeInt((int) (hl >>> 32));
            for (InsnNode node : switcher.values()) {
                w.writeInt(pcRev.applyAsInt(validate(node)) - self);
            }
        } else {
            w.writeInt(pcRev.applyAsInt(validate(def)) - self)
                    .writeInt(switcher.size());
            for (IntMap.Entry<InsnNode> entry : switcher.entrySet()) {
                w.writeInt(entry.getKey())
                        .writeInt(pcRev.applyAsInt(validate(entry.getValue())) - self);
            }
        }

        this.pad = -1;
    }

    private byte pad = -1;

    private long calculateShouldUseTable() {
        int nlabels = switcher.size();
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (PrimitiveIterator.OfInt itr = switcher.keySet().iterator(); itr.hasNext(); ) {
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
        for (IntMap.Entry<InsnNode> entry : switcher.entrySet()) {
            sb.append("               ").append(entry.getKey()).append(" : ").append(entry.getValue()).append('\n');
        }
        return sb.append("               default: ").append(def).append("\n            }").toString();
    }
}