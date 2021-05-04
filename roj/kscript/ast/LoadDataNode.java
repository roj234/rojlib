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
 * @since 2020/9/27 18:45
 */
public class LoadDataNode extends Node {
    public final KType data;
    private KType curr;

    public LoadDataNode(KType data) {
        super(OpCode.LOAD);
        this.data = data;
        this.curr = data.copy();
    }

    @Override
    public Node execute(Frame frame) {
        if(!data.equalsTo(curr)) {
            curr.copyFrom(data);
        }
        frame.push(curr);
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {
        // bipush, sipush, ldc
    }

    @Override
    public String toString() {
        return "Load(" + data + ')';
    }
}
