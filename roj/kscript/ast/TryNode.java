package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;

import javax.annotation.Nullable;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:50
 */
public final class TryNode extends Node {
    private Node err, fin, suc;

    public TryNode(LabelNode err, LabelNode fin, LabelNode suc) {
        super(OpCode.TRY_ENTER);
        this.err = err;
        this.fin = fin;
        this.suc = suc;
    }

    @Override
    protected void compile() {
        if(err.getClass() != LabelNode.class) return;

        err = err.next;
        fin = fin.next;
        suc = suc.next;
    }

    @Override
    public Node execute(Frame frame) {
        frame.tryCatch.push(this);
        return next;
    }

    public Node getEnd() {
        return suc.next;
    }

    @Nullable
    public Node fin() {
        return fin.next;
    }

    @Nullable
    public Node getHandler() {
        return err.next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
