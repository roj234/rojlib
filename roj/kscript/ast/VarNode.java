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
    Object name;

    public VarNode(String name, byte type) {
        super(type == GET ? Opcode.GET_VAR : Opcode.PUT_VAR);
        this.name = name;
    }

    @Override
    public Node execute(Frame frame) {
        if (code == Opcode.GET_VAR)
            frame.push(frame.get(name.toString()).setFlag(-1));
        else
            frame.put(name.toString(), frame.pop().setFlag(0));
        return next;
    }

    @Override
    Node replacement() {
        return name instanceof Node ? (Node) name : name instanceof Object[] ? (Node) ((Object[])name)[1] : this;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {
        // bipush, sipush, ldc
    }

    @Override
    public String toString() {
        return code.name() + "(" + name + ')';
    }
}
