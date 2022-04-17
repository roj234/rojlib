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

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.CstClass;
import roj.asm.type.ParamHelper;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * new / checkcast / instanceof / anewarray
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ClassInsnNode extends InsnNode implements IClassInsnNode {
    public ClassInsnNode(byte code) {
        super(code);
    }

    public ClassInsnNode(byte code, String owner) {
        super(code);
        this.owner = owner;
    }

    public ClassInsnNode(byte code, CstClass clazz) {
        super(code);
        this.owner = clazz.getValue().getString();
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case Opcodes.NEW:
            case Opcodes.CHECKCAST:
            case Opcodes.INSTANCEOF:
            case Opcodes.ANEWARRAY:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int nodeType() {
        return T_CLASS;
    }

    public String owner;

    public String owner() {
        return owner;
    }

    @Override
    public void owner(String clazz) {
        // noinspection all
        this.owner = clazz.toString();
    }

    @Override
    public void toByteArray(ConstantPool cw, ByteList w) {
        if (code == Opcodes.NEW && owner.startsWith("[")) {
            throw new IllegalArgumentException("The new instruction cannot be used to create an array.");
        }
        w.put(code).putShort(cw.getClassId(owner));
    }

    @Override
    public int nodeSize() {
        return 3;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder()
            .append(OpcodeUtil.toString0(code)).append(" ")
            .append(owner.endsWith(";") ? ParamHelper.parseField(owner) : owner);
        if (code == Opcodes.ANEWARRAY) sb.append("[]");
        return sb.toString();
    }
}