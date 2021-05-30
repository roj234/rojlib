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
public final class IncrNode extends Node {
    Object name;
    final int val;

    public IncrNode(String name, int val) {
        super(Opcode.INCREASE);
        this.name = name;
        this.val = val;
    }

    @Override
    public Node execute(Frame frame) {
        KType base = frame.get(name.toString());
        if (base.isInt()) {
            base.setIntValue(base.asInt() + val);
        } else {
            base.setDoubleValue(base.asDouble() + val);
        }
        return next;
    }

    @Override
    Node replacement() {
        return name instanceof Node ? (Node) name : name instanceof Object[] ? (Node) ((Object[])name)[1] : this;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return name + " += " + val;
    }
}
