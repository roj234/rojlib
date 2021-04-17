package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInteger;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:59
 */
public class IncrNode extends Node {
    final String name;
    final int val;

    public IncrNode(String name, int val) {
        super(ASTCode.IINC);
        this.name = name;
        this.val = val;
    }

    @Override
    public Node execute(Frame frame) {
        KType base = frame.ctx.get(name);
        if (base.isInteger()) {
            KInteger i = base.asKInteger();
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
}
