package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:59
 */
public final class LvtIncrNode extends Node {
    int ctxId, varId;
    final int val;

    public LvtIncrNode(int val) {
        super(Opcode.INCREASE);
        this.val = val;
    }

    @Override
    public Node execute(Frame frame) {
        KType base = frame.parents[ctxId].getIdx(varId);
        if (base.isInt()) {
            base.setIntValue(base.asInt() + val);
        } else {
            base.setDoubleValue(base.asDouble() + val);
        }
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "(" + ctxId + ',' + varId + ") += " + val;
    }
}
