package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:45
 */
public class VarNode extends Node {
    final String name;

    public VarNode(String name, boolean type) {
        super(type ? ASTCode.LOAD_VARIABLE : ASTCode.SET_VARIABLE);
        this.name = name;
    }

    @Override
    public Node execute(Frame frame) {
        if (getCode() == ASTCode.LOAD_VARIABLE)
            frame.stack.push(frame.ctx.get(name));
        else
            frame.ctx.put(name, frame.stack.pop());
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {
        // bipush, sipush, ldc
    }

    @Override
    public String toString() {
        return getCode().name() + "(" + name + ')';
    }
}
