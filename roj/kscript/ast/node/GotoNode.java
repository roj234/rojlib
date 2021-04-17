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
 * @since 2020/9/27 18:50
 */
public class GotoNode extends Node {
    final LabelNode target;

    GotoNode(ASTCode code, LabelNode label) {
        super(code);
        this.target = label;
    }

    public GotoNode(LabelNode label) {
        this(ASTCode.GOTO, label);
    }

    @Override
    public Node execute(Frame frame) {
        return target.next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "Goto " + target.next;
    }
}
