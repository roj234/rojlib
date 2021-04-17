package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.util.Helpers;

import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:02
 */
public class StringSwitchNode extends Node {
    final LabelNode def;
    final Map<String, LabelNode> map;

    public StringSwitchNode(LabelNode def, Map<String, LabelNode> map) {
        super(ASTCode.SWITCH_STRING);
        this.def = def;
        this.map = map;
    }

    @Override
    public Node execute(Frame frame) {
        return map.getOrDefault(frame.stack.pop().asString(), Helpers.cast(def == null ? next : def.next));
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
