package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.collect.CrossFinder;
import roj.collect.IntBiMap;
import roj.kscript.util.Variable;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/28 22:19
 */
public final class TryEndNode extends GotoNode {
    public TryEndNode(LabelNode realEnd) {
        super(OpCode.TRY_END, realEnd);
    }

    @Override
    protected void genDiff(CrossFinder<CrossFinder.Wrap<Variable>> var, IntBiMap<Node> idx) {
        super.genDiff(var, idx);
        target = null;
    }

    @Override
    public Node execute(Frame frame) {
        frame.applyDiff(diff);
        return null;
    }

    @Override
    public void toVMCode(Clazz clz, Method method, InsnList list) {

    }
}
