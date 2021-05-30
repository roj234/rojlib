package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/5/27 12:52
 */
public final class LvtVarNode extends Node {
    int ctxId, varId;

    public LvtVarNode(Opcode type) {
        super(type);
    }

    @Override
    public Node execute(Frame frame) {
        IContext ctx = frame.parents[ctxId];
        if (code == Opcode.GET_VAR)
            frame.push(ctx.getIdx(varId).setFlag(-1));
        else
            ctx.putIdx(varId, frame.pop().setFlag(0));
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {
        // bipush, sipush, ldc
    }

    @Override
    public String toString() {
        return code.name() + " LVT[" + ctxId + "][" + varId + ']';
    }
}
