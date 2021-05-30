package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 13:21
 */
public class LabelNode extends Node {
    public LabelNode() {
        super(Opcode.LABEL);
    }

    public LabelNode(LabelNode node) {
        super(Opcode.LABEL);
        this.next = node.next;
    }

    @Override
    public Node execute(Frame frame) {
        throw new IllegalStateException("This node should be executed!");
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
