package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.api.IObject;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/5/28 0:21
 */
public class MultiGetObject extends Node {
    String[] thus;

    protected MultiGetObject() {
        super(Opcode.GET_OBJ);
    }

    @Override
    public Node execute(Frame frame) {
        IObject obj = frame.last().asObject();
        for (String s : thus) {
            obj = obj.get(s).asObject();
        }
        frame.setLast(obj);

        return next;
    }

    @Override
    public void toVMCode(Clazz clz, Method method, InsnList list) {

    }
}
