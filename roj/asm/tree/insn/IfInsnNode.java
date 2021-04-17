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

import roj.asm.util.ConstantPool;
import roj.collect.IIntMap;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/2/19 14:33
 */
public final class IfInsnNode extends GotoInsnNode {
    public IfInsnNode(byte code, InsnNode node) {
        super(code, node);
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_icmpeq:
            case IF_icmpne:
            case IF_icmplt:
            case IF_icmpge:
            case IF_icmpgt:
            case IF_icmple:
            case IF_acmpeq:
            case IF_acmpne:
            case IFNULL:
            case IFNONNULL:
                return true;
        }
        return false;
    }

    @Override
    public boolean review(IIntMap<InsnNode> pcRev) {
        delta = pcRev.getInt(target = validate(target)) - pcRev.getInt(this);
        return false;
    }

    public void toByteArray(ConstantPool cw, ByteWriter w) {
        w.writeByte(code).writeShort(delta);
    }
}