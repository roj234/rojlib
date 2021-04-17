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
 * @since 2020/9/27 18:45
 */
public class LineNumber extends Node {
    public int line;

    public LineNumber() {
        super(ASTCode.LINE);
    }

    public LineNumber(int line) {
        super(ASTCode.LINE);
        this.line = line;
    }

    @Override
    public Node execute(Frame frame) {
        frame.setLine(line);
        return next;
    }

    @Override
    public void toVMCode(Clazz clz, Method method, InsnList list) {

    }

    public String toString() {
        return "//Ln " + line;
    }
}
