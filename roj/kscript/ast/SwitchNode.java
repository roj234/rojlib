package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.collect.CrossFinder;
import roj.collect.IntBiMap;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.Variable;
import roj.util.Helpers;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:02
 */
public final class SwitchNode extends Node {
    private Node def;
    private VInfo diff;
    private final SwitchMap map;

    public SwitchNode(Node def, SwitchMap map) {
        super(OpCode.SWITCH);
        this.def = def;
        this.map = Helpers.cast(map);
    }

    @Override
    protected void compile() {
        if(def.getClass() != LabelNode.class)
            return;
        def = def.next;
        map.stripLabels();
    }

    @Override
    protected void genDiff(CrossFinder<CrossFinder.Wrap<Variable>> var, IntBiMap<Node> idx) {
        List<CrossFinder.Wrap<Variable>> self = var._collect_modifiable_int_(idx.getByValue(this)),
            dest = var._collect_modifiable_int_(idx.getByValue(def));
        if(self != dest) {
            diff = NodeUtil.calcDiff(self, dest);
        }
        map.genDiff(self, var, idx);
    }

    @Override
    public Node execute(Frame frame) {
        return map.getAndApply(frame, def, diff);
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "Switch " + map + " or: " + def;
    }
}
