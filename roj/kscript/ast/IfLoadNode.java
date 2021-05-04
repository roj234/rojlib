package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KBool;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:50
 */
public final class IfLoadNode extends Node {
    final byte type;

    public IfLoadNode(short type) {
        super(OpCode.IF_LOAD);
        if(type == IfNode.TRUE - 500)
            throw new IllegalArgumentException("NO IS_TRUE available");
        this.type = (byte) (type - 500);
    }

    @Override
    public Node execute(Frame frame) {
        frame.push(KBool.valueOf(IfNode.calcIf(frame, type)));

        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "If_Load " + Symbol.byId((short) (type + 500));
    }
}
