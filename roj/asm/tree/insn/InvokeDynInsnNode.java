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
import roj.asm.cst.CstDynamic;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class InvokeDynInsnNode extends IInvokeInsnNode {
    public InvokeDynInsnNode() {
        super(Opcodes.INVOKEDYNAMIC);
    }

    public InvokeDynInsnNode(CstDynamic ref, int type) {
        super(Opcodes.INVOKEDYNAMIC);
        this.tableIdx = ref.tableIdx;
        this.name = ref.getDesc().getName().getString();
        this.rawParam = ref.getDesc().getType().getString();
    }

    @Override
    public int nodeType() {
        return T_INVOKE_DYNAMIC;
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.INVOKEDYNAMIC;
    }
    /**
     * bootstrap table index
     */
    public char tableIdx;

    /**
     * The third and fourth operand bytes of each invokedynamic instruction must have the value zero. <br>
     * Thus, we ignore it again(Previous in InvokeItfInsnNode).
     */
    @Override
    public void toByteArray(ConstantPool cw, ByteWriter w) {
        if (params != null) {
            params.add(returnType);
            rawParam = ParamHelper.getMethod(params);
            params.remove(params.size() - 1);
        }
        w.put(code).putShort(cw.getInvokeDynId(tableIdx, name, rawParam)).putShort(0);
    }

    @Override
    public int nodeSize() {
        return 5;
    }

    public String toString() {
        initPar();
        StringBuilder sb = new StringBuilder(super.toString()).append(" #").append((int) tableIdx).append(' ').append(returnType).append(" ?").append('.').append(name).append('(');
        if (!params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                Type par = params.get(i);
                sb.append(par).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(')').toString();
    }

    @Override
    public void rawDesc(String desc) {
        throw new UnsupportedOperationException("InvokeDyn does not support 'classed' descriptor");
    }
}