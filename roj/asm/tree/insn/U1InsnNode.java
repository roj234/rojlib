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
import roj.util.ByteWriter;

// bipush / newarray
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/24 23:21
 */
public final class U1InsnNode extends InsnNode implements IIndexInsnNode {
    public U1InsnNode(byte code, int index) {
        super(code);
        this.index = (byte) index;
    }

    /**
     * for new array
     * T_BOOLEAN4
     * T_CHAR	5
     * T_FLOAT	6
     * T_DOUBLE	7
     * T_BYTE	8
     * T_SHORT	9
     * T_INT	10
     * T_LONG	11
     */
    private byte index;

    @Override
    public int nodeType() {
        int c = code & 0xFF;
        return (c >= 0x15 && c <= 0x19) || (c >= 0x36 && c <= 0x3a) ? T_LOAD_STORE : T_OTHER;
    }

    public int getIndex() {
        return index & 0xFF;
    }

    @Override
    public void setIndex(int index) {
        if(index < 0 || index > 255)
            throw new IndexOutOfBoundsException("U1InsnNode supports [0,255]");
        this.index = (byte) index;
    }

    public void toByteArray(ByteWriter w) {
        w.writeByte(code).writeByte(index);
    }

    public String toString() {
        return Opcodes.toString0(code, index);
    }
}