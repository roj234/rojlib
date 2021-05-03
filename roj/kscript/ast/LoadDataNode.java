package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.collect.IntMap;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:45
 */
public class LoadDataNode extends Node {
    final KType data;

    static IntMap<KType> ddd = new IntMap<>();

    int j;
    static int i;

    public LoadDataNode(KType data) {
        super(OpCode.LOAD);
        this.data = data;
        j = i++;
        ddd.put(j, data.copy());
    }

    @Override
    public Node execute(Frame frame) {
        if(!ddd.get(j).equalsTo(data))
            System.err.println("#" + j + " : NEQ orig " + ddd.get(j) + " now" + data);
        frame.push(data/*.copy()*/);
        return next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {
        // bipush, sipush, ldc
    }

    @Override
    public String toString() {
        return "Load(" + data + ')';
    }
}
