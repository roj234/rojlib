package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:50
 */
public final class TryNode extends Node {
    public Node handler, fin, end;

    public TryNode(LabelNode handler, LabelNode fin, LabelNode end) {
        super(Opcode.TRY_ENTER);
        this.handler = handler;
        this.fin = fin;
        this.end = end;
    }

    @Override
    protected void compile() {
        if(handler.getClass() != LabelNode.class) return;

        handler = handler.next;
        fin = fin.next;
        end = end.next;
    }

    @Override
    public Node execute(Frame frame) {
        frame.tryCatch.push(this);
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
