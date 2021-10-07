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
        return T_SWITCH;
    }

    public InsnNode         def;
    public IntMap<InsnNode> switcher;

    private ToIntFunction<InsnNode> pcRev;

    private byte pad = -1;

    public void pad(int codeLength, ToIntFunction<InsnNode> pcRev) {
        this.pad = (byte) (3 - (codeLength & 3));
        this.pcRev = pcRev;
    }

    @Override
    public int nodeSize() {
        if(pad == -1) {
            throw new IllegalStateException();
        }
        return code == Opcodes.TABLESWITCH ?
                1 + pad + 4 + 8 + (switcher.size() << 2) :
                1 + pad + 8 + (switcher.size() << 3);
    }

    @Override
    public void toByteArray(ConstantWriter cw, ByteWriter w) {
        if(pad == -1) {
            throw new IllegalStateException();
        }

        ToIntFunction<InsnNode> pcRev = this.pcRev;
        int self = pcRev.applyAsInt(this);

        w.writeByte(code).list.pos(w.list.pos() + pad);
        if (this.code == Opcodes.TABLESWITCH) {
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (PrimitiveIterator.OfInt itr = switcher.keySet().iterator(); itr.hasNext(); ) {
                int val = itr.nextInt();
                if (val > hi) hi = val;
                if (val < lo) lo = val;
            }
            w.writeInt(pcRev.applyAsInt(validate(def)) - self)
                    .writeInt(lo).writeInt(hi);
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

        this.pcRev = null;
        this.pad = -1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(" {\n");
        for (IntMap.Entry<InsnNode> entry : switcher.entrySet()) {
            sb.append("               ").append(entry.getKey()).append(" : ").append(entry.getValue()).append('\n');
        }
        return sb.append("               default: ").append(def).append("\n            }").toString();
    }
}