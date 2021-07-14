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

package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstRefItf;
import roj.asm.type.ParamHelper;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class InvokeItfInsnNode extends InvokeInsnNode {
    public InvokeItfInsnNode() {
        super(Opcodes.INVOKEINTERFACE);
    }

    public InvokeItfInsnNode(String str) {
        super(Opcodes.INVOKEINTERFACE, str);
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
        if(mainVer < 52)
            throw new IllegalArgumentException("Interface supported since version 53");
        super.verify(list, index, mainVer);
    }

    public byte count;

    /**
     * indexbyte1
     * indexbyte2
     * count
     * 0
     */
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        toByteArray(w);

        // The fourth operand byte of each invokeinterface instruction must have the value zero.
        // Thus, we ignore it.
        int cnt = 1;
        for (int i = 0; i < params.size(); i++) {
            cnt += params.get(i).length();
        }
        count = (byte) cnt;

        // The value of the count operand of each invokeinterface instruction must reflect the number of local variables necessary
        // to store the arguments to be passed to the interface method,
        // as implied by the descriptor of the CONSTANT_NameAndType_info structure
        // referenced by the CONSTANT_InterfaceMethodref constant pool entry.

        params.add(returnType);
        mid = pool.getItfRefId(owner, name, ParamHelper.getMethod(params));
        params.remove(params.size() - 1);
    }

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeByte(count).writeByte((byte) 0);
    }
}