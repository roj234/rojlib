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
 * <p>
 * 操作节点
 *
 * @author Roj233
 * @since 2020/9/27 12:30
 */
public abstract class Node {
    Opcode code;
    public Node next;

    protected Node(Opcode code) {
        this.code = code;
    }

    public abstract Node execute(Frame frame);

    public abstract void toVMCode(Clazz clz, Method method, InsnList list);

    protected void compile() {}

    @Override
    public String toString() {
        return code.toString();
    }

    public final Opcode getCode() {
        return code;
    }

    Node replacement() {
        return this;
    }

    protected void genDiff(CrossFinder<CrossFinder.Wrap<Variable>> var, IntBiMap<Node> idx) {}
}
