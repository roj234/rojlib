package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.collect.CrossFinder;
import roj.collect.IntBiMap;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:50
 */
public class GotoNode extends Node {
    Node target;
    VInfo diff;

    GotoNode(Opcode code, LabelNode label) {
        super(code);
        this.target = label;
    }

    public GotoNode(LabelNode label) {
        this(Opcode.GOTO, label);
    }

    @Override
    protected void compile() {
        if(target.getClass() == LabelNode.class) {
            target = target.next;
            if(target instanceof VarNode && ((VarNode) target).name instanceof Node) {
                target = (Node) ((VarNode) target).name;
            } else
            if(target instanceof IncrNode && ((IncrNode) target).name instanceof Node) {
                target = (Node) ((IncrNode) target).name;
            }
        }
    }

    @Override
    protected void genDiff(CrossFinder<CrossFinder.Wrap<Variable>> var, IntBiMap<Node> idx) {
        List<CrossFinder.Wrap<Variable>> self = var._collect_modifiable_int_(idx.getByValue(this)),
            dest = var._collect_modifiable_int_(idx.getByValue(target));
        if(self != dest) {
            diff = NodeUtil.calcDiff(self, dest);
        }
    }

    @Override
    public Node execute(Frame frame) {
        frame.applyDiff(diff);
        return target;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "Goto " + target;
    }

    public void checkNext(Node prev) {
        if(target == next)
            prev.next = target;
    }
}
