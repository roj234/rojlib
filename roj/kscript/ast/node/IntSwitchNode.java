package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.collect.IntMap;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.util.Helpers;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:48
 */
public class IntSwitchNode extends Node {
    final LabelNode def;
    final IntMap<LabelNode> map;

    public IntSwitchNode(LabelNode def, IntMap<LabelNode> map) {
        super(ASTCode.SWITCH_INT);
        this.def = def;
        this.map = map;
    }

    @Override
    public Node execute(Frame frame) {
        return map.getOrDefault(frame.stack.pop().asInteger(), Helpers.cast(def == null ? next : def.next));
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
