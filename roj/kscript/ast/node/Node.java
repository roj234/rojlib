package roj.kscript.ast.node;

import roj.annotation.Internal;
import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 操作节点
 *
 * @author Roj233
 * @since 2020/9/27 12:30
 */
public abstract class Node {
    private ASTCode code;

    @Internal
    public void setCode(ASTCode code) {
        this.code = code;
    }

    protected Node next;

    protected Node(ASTCode code) {
        this.code = code;
    }

    public abstract Node execute(Frame frame);

    public abstract void toVMCode(Clazz clz, Method method, InsnList list);

    @Override
    public String toString() {
        return code.toString();
    }

    public void next(Node node) {
        this.next = node;
    }

    public Node next() {
        return next;
    }

    public ASTCode getCode() {
        return code;
    }
}
