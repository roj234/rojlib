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
 * @since 2020/9/27 13:21
 */
public class LabelNode extends Node {
    public static final LabelNode TRY_CATCH_ENDPOINT = new LabelNode() {
        @Override
        public void next(Node node) {
            throw new IllegalArgumentException();
        }
    };

    public LabelNode() {
        super(ASTCode.LABEL);
    }

    public LabelNode(LabelNode node) {
        super(ASTCode.LABEL);
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
