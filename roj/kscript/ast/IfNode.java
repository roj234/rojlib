package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.collect.CrossFinder;
import roj.collect.IntBiMap;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
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
public final class IfNode extends Node {
    final byte type;
    Node target;
    VInfo diff;

    public static final short TRUE = 513;

    public IfNode(short type, LabelNode target) {
        super(Opcode.IF);
        this.type = (byte) (type - 500);
        this.target = target;
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
        boolean _if = calcIf(frame, type);
        if(_if)
            return next;
        frame.applyDiff(diff);
        return target;
    }

    static boolean calcIf(Frame frame, byte type) {
        KType b = frame.pop();

        boolean v = false;
        switch (type) {
            case TRUE - 500:
                v = b.asBool();
                break;
            case Symbol.lss - 500:
            case Symbol.gtr - 500:
            case Symbol.geq - 500:
            case Symbol.leq - 500: {
                KType a = frame.pop();
                if (!a.canCastTo(Type.INT) || !b.canCastTo(Type.INT))
                    throw new IllegalArgumentException("operand is not number: " + a.getClass().getName() + ", " + b.getClass().getName());

                if (!a.isInt() || !b.isInt()) {
                    final double aa = a.asDouble(), bb = b.asDouble();
                    switch (type) {
                        case Symbol.lss - 500:
                            v = aa < bb;
                            break;
                        case Symbol.gtr - 500:
                            v = aa > bb;
                            break;
                        case Symbol.geq - 500:
                            v = aa >= bb;
                            break;
                        case Symbol.leq - 500:
                            v = aa <= bb;
                            break;
                    }
                } else {
                    final int aa = a.asInt(), bb = b.asInt();
                    switch (type) {
                        case Symbol.lss - 500:
                            v = aa < bb;
                            break;
                        case Symbol.gtr - 500:
                            v = aa > bb;
                            break;
                        case Symbol.geq - 500:
                            v = aa >= bb;
                            break;
                        case Symbol.leq - 500:
                            v = aa <= bb;
                            break;
                    }
                }
            }
            break;
            case Symbol.feq - 500: {
                KType a = frame.pop();
                switch (b.getType()) {
                    case BOOL:
                    case NULL:
                    case FUNCTION:
                    case UNDEFINED:
                    case OBJECT:
                    case ERROR:
                        v = a == b; // boolean compare
                        break;
                    case DOUBLE:
                    case INT:
                        switch (a.getType()) {
                            case INT:
                            case DOUBLE:
                                v = a.equalsTo(b);
                                break;
                        }
                        break;
                    case STRING:
                        v = b.getType() == Type.STRING && a.asString().equals(b.asString());
                        break;
                }
            }
            break;
            case Symbol.equ - 500:
            case Symbol.neq - 500: {
                KType a = frame.pop();
                v = (type == (Symbol.equ - 500)) == a.equalsTo(b);
            }
            break;
        }

        return v;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        String k;

        switch (type) {
            case TRUE - 500:
                k = "false";
                break;
            case Symbol.lss - 500:
                k = ">=";
                break;
            case Symbol.gtr - 500:
                k = "<=";
                break;
            case Symbol.geq - 500:
                k = "<";
                break;
            case Symbol.leq - 500:
                k = ">";
                break;
            case Symbol.feq - 500:
                k = "!===";
                break;
            case Symbol.equ - 500:
                k = "!=";
                break;
            case Symbol.neq - 500:
                k = "==";
                break;
            default:
                k = "Undefined" + type;
                break;
        }
        return "If " + k + " => " + target;
    }
}
