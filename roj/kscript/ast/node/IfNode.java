package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KType;
import roj.kscript.type.Type;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 18:50
 */
public class IfNode extends Node {
    final byte type;
    final LabelNode target;

    public static final byte
            FEQ = Symbol.feq - 500,
            EQU = Symbol.equ - 500,
            NEQ = Symbol.neq - 500,
            LSS = Symbol.lss - 500,
            LEQ = Symbol.leq - 500,
            GTR = Symbol.gtr - 500,
            GEQ = Symbol.geq - 500,
            IS_TRUE = -99;

    public IfNode(byte type, LabelNode target) {
        super(ASTCode.IF);
        this.type = type;
        this.target = target;
    }

    @Override
    public Node execute(Frame frame) {
        KType b = frame.stack.pop();

        boolean result = false;
        switch (type) {
            case IS_TRUE:
                result = b.asBoolean();
                break;
            case FEQ: {
                KType a = frame.stack.pop();
                switch (b.getType()) {
                    case BOOL:
                    case NULL:
                    case FUNCTION:
                    case INSTANCE:
                    case UNDEFINED:
                    case OBJECT:
                    case ERROR:
                        result = a == b; // boolean compare
                        break;
                    case DOUBLE:
                    case NUMBER:
                        switch (a.getType()) {
                            case NUMBER:
                            case DOUBLE:
                                result = a.equalsTo(b);
                                break;
                        }
                        break;
                    case STRING:
                        result = b.getType() == Type.STRING && a.asString().equals(b.asString());
                        break;
                }
            }
            break;
            case EQU:
            case NEQ: {
                KType a = frame.stack.pop();
                result = (type == EQU) == a.equalsTo(b);
            }
            break;
            case GTR:
            case LEQ:
            case LSS:
            case GEQ: {
                KType a = frame.stack.pop();
                if (!a.canCastTo(Type.NUMBER) || !b.canCastTo(Type.NUMBER))
                    throw new IllegalArgumentException("operand is not number " + a + ",  " + b);

                if (!a.isInteger() || !b.isInteger()) {
                    final double aa = a.asDouble(), bb = b.asDouble();
                    switch (type) {
                        case LSS:
                            result = aa < bb;
                            break;
                        case LEQ:
                            result = aa <= bb;
                            break;
                        case GTR:
                            result = aa > bb;
                            break;
                        case GEQ:
                            result = aa >= bb;
                            break;
                    }
                } else {
                    final int aa = a.asInteger(), bb = b.asInteger();
                    switch (type) {
                        case LSS:
                            result = aa < bb;
                            break;
                        case LEQ:
                            result = aa <= bb;
                            break;
                        case GTR:
                            result = aa > bb;
                            break;
                        case GEQ:
                            result = aa >= bb;
                            break;
                    }
                }
            }
            break;
        }

        return result ? next : target.next;
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }

    @Override
    public String toString() {
        return "IfNot " + (type == -99 ? "true" : Symbol.byId((short) (type + 500))) + " Goto " + target.next;
    }
}
