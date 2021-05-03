package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:59
 */
public final class IncrNode extends Node {
    final String name;
    final int val;

    public IncrNode(String name, int val) {
        super(OpCode.INCREASE);
        this.name = name;
        this.val = val;
    }

    @Override
    public Node execute(Frame frame) {
        KType base = frame.get(name);
        if (base.isInt()) {
            KInt i = base.asKInt();
            i.value += val;
        } else {
            KDouble i = base.asKDouble();
            i.value += val;
        }
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return name + " += " + val;
    }
}
