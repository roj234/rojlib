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
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/24 23:21
 */
public final class IncrInsnNode extends InsnNode implements IIndexInsnNode {
    public IncrInsnNode(int variableId, int amount) {
        super(Opcodes.IINC);
        this.variableId = (char) variableId;
        this.amount = (short) amount;
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.IINC;
    }

    public char variableId;
    public short amount;

    @Override
    public int nodeType() {
        return T_IINC;
    }

    @Override
    public int getIndex() {
        return variableId;
    }

    @Override
    public void setIndex(int index) {
        if(index < 0 || index > 65535)
            throw new IllegalArgumentException("IncrInsnNode supports [0,65535]");

        if(variableId > 255) {
            if(index < 255)
                throw new IllegalArgumentException("Check [wide] node and manually set index");
        } else if (index > 255)
            throw new IllegalArgumentException("Check [wide] node and manually set index");
        this.variableId = (char) index;
    }

    @Override
    public void toByteArray(ConstantWriter cw, ByteWriter w) {
        w.writeByte(code);
        if (variableId > 255 || amount != (byte) amount) {
            w.writeShort(variableId).writeShort(amount);
        } else {
            w.writeByte((byte) variableId).writeByte((byte) amount);
        }
    }

    @Override
    public int nodeSize() {
        return variableId > 255 || amount != (byte) amount ? 5 : 3;
    }

    public String toString() {
        return Opcodes.toString0(code, (int) variableId, amount);
    }
}