/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: InvokeInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstRefItf;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.asm.util.type.ParamHelper;
import roj.util.ByteWriter;

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