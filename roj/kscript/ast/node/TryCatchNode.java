package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.kscript.ast.api.TryCatchInfo;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:50
 */
public class TryCatchNode extends Node {
    private final TryCatchInfo info;

    public TryCatchNode(LabelNode handler, LabelNode fin, LabelNode end) {
        super(ASTCode.TRY_ENTER);
        this.info = new TryCatchInfo(handler, fin, end);
    }

    @Override
    public Node execute(Frame frame) {
        frame.tryCatch.push(info);
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
