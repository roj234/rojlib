package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:45
 */
public class VarNode extends Node {
    public static final byte GET = 0, SET = 1;
    final String name;

    public VarNode(String name, byte type) {
        super(type == GET ? OpCode.GET_VAR : OpCode.PUT_VAR);
        this.name = name;
    }

    @Override
    public Node execute(Frame frame) {
        if (getCode() == OpCode.GET_VAR)
            frame.push(frame.get(name));
        else
            frame.put(name, frame.pop());
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
