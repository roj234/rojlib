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
import roj.asm.cst.CstClass;
import roj.asm.util.ConstantPool;
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:28
 */
public final class MDArrayInsnNode extends InsnNode implements IIndexInsnNode, IClassInsnNode {
    public MDArrayInsnNode() {
        super(Opcodes.MULTIANEWARRAY);
    }

    public MDArrayInsnNode(CstClass clazz, int dimension) {
        super(Opcodes.MULTIANEWARRAY);
        this.owner = clazz.getValue().getString();
        this.dimension = dimension;
    }

    @Override
    public int nodeType() {
        return T_MULTIANEWARRAY;
    }

    public String owner;
    public int dimension;

    public int getIndex() {
        return dimension;
    }

    @Override
    public void setIndex(int index) {
        throw new UnsupportedOperationException("Cannot change dimension by setter, manually cast plz");
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.MULTIANEWARRAY;
    }

    @Override
    public void owner(String clazz) {
        // noinspection all
        this.owner = clazz.toString();
    }

    public String owner() {
        return owner;
    }

    @Override
    public void toByteArray(ConstantPool cw, ByteWriter w) {
        w.put(code)
         .putShort(cw.getClassId(owner))
         .put((byte) this.dimension);
    }

    @Override
    public int nodeSize() {
        return 4;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(' ').append(owner);
        for (int i = 0; i < dimension; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }
}