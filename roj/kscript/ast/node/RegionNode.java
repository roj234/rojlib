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
 * @since 2020/10/17 1:22
 */
public class RegionNode extends Node {
    final int id;

    public RegionNode(int id) {
        super(ASTCode.REGION);
        this.id = id;
    }

    @Override
    public Node execute(Frame frame) {
        frame.ctx.enterRegion(id);
        return next;
    }

    @Override
    public void toVMCode(Clazz clz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "//Region " + id;
    }
}
