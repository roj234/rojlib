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
import roj.asm.util.ConstantPool;
import roj.collect.BSLowHeap;
import roj.util.ByteList;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 1:55
 */
public final class SwitchInsnNode extends InsnNode {
    public SwitchInsnNode(byte code) {
        super(code);
        this.switcher = new BSLowHeap<>(null);
    }

    public SwitchInsnNode(byte code, InsnNode def, List<SwitchEntry> switcher) {
        super(code);
        this.def = def;
        this.switcher = switcher;
        switcher.sort(null);
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

    public InsnNode          def;
    public List<SwitchEntry> switcher;

    private byte pad = -1;

    public void pad(int codeLength) {
        this.pad = (byte) (3 - (codeLength & 3));
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
    public void toByteArray(ConstantPool cw, ByteList w) {
        if(pad == -1) {
            throw new IllegalStateException();
        }

        int self = bci;

        // 共享... 问题在这
        byte[] data = w.put(code).list;
        int pos = w.wIndex();
        w.wIndex(pos + pad);
        for (int i = 0; i < pad; i++) {
            data[pos++] = 0;
        }
        if (this.code == Opcodes.TABLESWITCH) {
            int lo = switcher.get(0).key;
            int hi = switcher.get(switcher.size() - 1).key;

            w.putInt(validate(def).bci - self)
             .putInt(lo).putInt(hi);
            for (SwitchEntry e : switcher) {
                w.putInt(e.getBci() - self);
            }
        } else {
            w.putInt(validate(def).bci - self)
                    .putInt(switcher.size());
            for (SwitchEntry entry : switcher) {
                w.putInt(entry.key)
                        .putInt(entry.getBci() - self);
            }
        }

        this.pad = -1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(" {\n");
        for (SwitchEntry entry : switcher) {
            sb.append("               ").append(entry.key).append(" : ").append(entry.node).append('\n');
        }
        return sb.append("               default: ").append(def).append("\n            }").toString();
    }

}