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
    public static final LabelNode _INT_FLAG_ = new LabelNode() {
        @Override
        public String toString() {
            return "tce_???";
        }
    };

    public LabelNode() {
        super(OpCode.LABEL);
    }

    public LabelNode(LabelNode node) {
        super(OpCode.LABEL);
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
