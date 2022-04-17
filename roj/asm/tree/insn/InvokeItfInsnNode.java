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
import roj.asm.cst.CstRefItf;
import roj.asm.type.ParamHelper;
import roj.asm.util.ConstantPool;
import roj.asm.util.InsnList;
import roj.util.ByteList;

/**
 * Invoke interface method
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class InvokeItfInsnNode extends InvokeInsnNode {
    public InvokeItfInsnNode() {
        super(Opcodes.INVOKEINTERFACE);
    }

    public InvokeItfInsnNode(String str) {
        super(Opcodes.INVOKEINTERFACE, str);
    }

    public InvokeItfInsnNode(String owner, String name, String types) {
        super(Opcodes.INVOKEINTERFACE, owner, name, types);
    }

    public InvokeItfInsnNode(CstRefItf ref, short flag) {
        super(Opcodes.INVOKEINTERFACE, ref);
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.INVOKEINTERFACE;
    }

    @Override
    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
        // Only class file version 52.0 or above, can supports (CstRefItf).
        if(mainVer < 52)
            throw new IllegalArgumentException("Interface supported since version 53");
        super.verify(list, index, mainVer);
    }

    /**
     * index1 index2 count 0
     */
    @Override
    public int nodeSize() {
        return 5;
    }

    public void toByteArray(ConstantPool cw, ByteList w) {
        String desc = rawDesc();
        w.put(code).putShort(cw.getItfRefId(owner, name, desc));

        // The value of the count operand of each invokeinterface instruction must reflect the number of local variables necessary
        // to store the arguments to be passed to the interface method.
        int cnt = 1;
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                cnt += params.get(i).length();
            }
        } else {
            cnt += ParamHelper.paramSize(desc);
        }
        w.put((byte) cnt)
         // The fourth operand byte of each invokeinterface instruction must have the value zero.
         // Thus, we ignore it.
         .put((byte) 0);
    }
}