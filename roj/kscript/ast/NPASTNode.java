package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.type.*;
import roj.kscript.util.ScriptException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * 标准AST节点 <BR>
 *
 * @author Roj233
 * @since 2020/9/27 13:23
 */
public final class NPASTNode extends Node {
    public NPASTNode(OpCode code) {
        super(code);
    }

    @Override
    public Node execute(Frame frame) {
        switch (code) {
            case ADD_ARRAY:
                array_add(frame);
                break;
            case MOD: {
                KType b = frame.pop();
                KType a = frame.last();

                int v = b.isInt() ? a.asInt() % b.asInt() : 0;

                if(a.spec() != 2) {
                    frame.setLast(KInt.OnStack.valueOf(v));
                } else {
                    a.asKInt().value = v;
                }
            }
            break;
            case XOR:
            case OR:
            case AND: {
                KType b = frame.pop();
                KType a = frame.last();

                boolean val = a.getType() == Type.BOOL;
                if (val) {
                    boolean v = false;
                    boolean aa = a.asBool(), bb = b.asBool();
                    switch (code) {
                        case XOR:
                            v = aa ^ bb;
                            break;
                        case OR:
                            v = aa | bb;
                            break;
                        case AND:
                            v = aa & bb;
                            break;
                    }
                    frame.setLast(KBool.valueOf(v));
                } else {
                    int v = 0;
                    int aa = a.asInt(), bb = b.asInt();
                    switch (code) {
                        case XOR:
                            v = aa ^ bb;
                            break;
                        case OR:
                            v = aa | bb;
                            break;
                        case AND:
                            v = aa & bb;
                            break;
                    }

                    if(a.spec() != 2) {
                        frame.setLast(KInt.OnStack.valueOf(v));
                    } else {
                        a.asKInt().value = v;
                    }
                }
            }
            break;
            case POW:
            case MUL:
            case DIV:
            case SUB:
            case ADD: {
                KType b = frame.pop();
                KType a = frame.last();

                if (code == OpCode.ADD && (a.isString() || b.isString())) { // string append
                    StringBuilder sb;
                    if (a.getType() != Type.JAVA_OBJECT || !(a.asJavaObject(Object.class).getObject() instanceof StringBuilder)) {
                        frame.setLast(new KJavaObject<>(sb = new StringBuilder(a.asString())));
                    } else {
                        sb = a.asJavaObject(StringBuilder.class).getObject();
                    }
                    sb.append(b.asString());
                    break;
                }

                // Number
                if (!b.isInt() || !a.isInt() || code == OpCode.POW) {
                    double aa = b.asDouble();
                    double bb = a.asDouble();

                    switch (code) {
                        case POW:
                            aa = Math.pow(aa, bb);
                            break;
                        case MUL:
                            aa = aa * bb;
                            break;
                        case DIV:
                            aa = aa / bb;
                            break;
                        case ADD:
                            aa = aa + bb;
                            break;
                        case SUB:
                            aa = aa - bb;
                            break;
                    }

                    if(a.spec() != 4) {
                        frame.setLast(KDouble.OnStack.valueOf(aa));
                    } else {
                        a.asKDouble().value = aa;
                    }
                } else {
                    int aa = b.asInt();
                    int bb = a.asInt();

                    switch (code) {
                        case MUL:
                            aa = aa * bb;
                            break;
                        case DIV:
                            aa = aa / bb;
                            break;
                        case ADD:
                            aa = aa + bb;
                            break;
                        case SUB:
                            aa = aa - bb;
                            break;
                    }

                    if(a.spec() != 2) {
                        frame.setLast(KInt.OnStack.valueOf(aa));
                    } else {
                        a.asKInt().value = aa;
                    }
                }
            }
            break;
            case GET_OBJ:
                object__get(frame);
                break;
            case PUT_OBJ:
                object__put(frame);
                break;
            case DUP2: {
                // a,b => a,b , a,b
                KType a = frame.last(1);
                KType b = frame.last();
                frame.push(a.markImmutable(true));
                frame.push(b.markImmutable(true));
            }
            break;
            case DUP: {
                frame.push(frame.last().markImmutable(true));
            }
            break;
            case NEGATIVE: {
                KType a = frame.last();

                if(a.isInt()) {
                    int aa = -a.asInt();
                    if(a.spec() != 2) {
                        frame.setLast(KInt.OnStack.valueOf(aa));
                    } else {
                        a.asKInt().value = aa;
                    }
                } else {
                    double aa = -a.asDouble();
                    if(a.spec() != 4) {
                        frame.setLast(KDouble.OnStack.valueOf(aa));
                    } else {
                        a.asKDouble().value = aa;
                    }
                }
            }
            break;
            case NOT: {
                frame.setLast(KBool.valueOf(!frame.last().asBool()));
            }
            break;
            case REVERSE: {
                KType a = frame.last();
                a.asInt();

                int aa = a.isInt() ? ~a.asInt() : 0;
                if(a.spec() != 2) {
                    frame.setLast(KInt.OnStack.valueOf(aa));
                } else {
                    a.asKInt().value = aa;
                }
            }
            break;
            case SHIFT_L:
            case SHIFT_R:
            case U_SHIFT_R: {
                KType count = frame.pop();
                KType num = frame.last();

                num.asInt();
                if (!num.isInt()) {
                    if(num.spec() != 2) {
                        frame.setLast(KInt.OnStack.valueOf(0));
                    } else {
                        num.asKInt().value = 0;
                    }
                } else {
                    int val = count.asInt();

                    int it = num.asInt();
                    switch (code) {
                        case U_SHIFT_R:
                            it >>>= val;
                            break;
                        case SHIFT_R:
                            it >>= val;
                            break;
                        case SHIFT_L:
                            it <<= val;
                            break;
                    }

                    if(num.spec() != 2) {
                        frame.setLast(KInt.OnStack.valueOf(it));
                    } else {
                        num.asKInt().value = it;
                    }
                }
            }
            break;
            case POP: {
                frame.pop();
            }
            break;
            case ARGUMENTS: {
                frame.push(frame.args);
            }
            break;
            case THIS: {
                frame.push(frame.$this);
            }
            break;
            case TRY_EXIT: {
                TryNode info = frame.tryCatch.last();
                if (info.fin() != null)
                    throw ScriptException.TRY_EXIT;
                return frame.tryCatch.pop().getEnd();
            }
            case RETURN_EMPTY:
                return null;
            case RETURN: {
                frame.result = frame.pop();
                return null;
            }
            case THROW: {
                // no pop since stack will be cleared
                throw frame.last().asKError().getOrigin();
            }
            case SWAP: {
                Frame.V v = frame.tail(2);
                Frame.V pr = v.prev;

                KType t = pr.v;

                pr.v = v.v;
                v.v = t;
            }
            break;
            case SWAP3: {
                // a b c d => d a b c
                Frame.V v = frame.tail(4);

                // a b c
                frame.tail(v.prev);

                Frame.V p3 = v.prev.prev.prev;

                Frame.V p4 = p3.prev;

                p3.prev = v;
                v.prev = p4;
            }
            break;
            case INSTANCE_OF: {
                KType clazz = frame.pop();
                KType instance = frame.last();

                boolean result = instance.canCastTo(Type.OBJECT) && instance.asObject().isInstanceOf(clazz.asObject());
                frame.setLast(KBool.valueOf(result));
            }
            break;
            default:
                throw new IllegalArgumentException("Unsupported operator " + code);
        }

        return next;
    }

    private void object__get(Frame stack) {
        KType name = stack.pop();
        KType base = stack.last();

        if(base.canCastTo(Type.ARRAY) && name.isInt()) {
            stack.setLast(base.asArray().get(name.asInt()));
        } else {
            stack.setLast(base.asObject().get(name.asString()));
        }
    }

    private void object__put(Frame stack) {
        KType val = stack.pop();
        KType name = stack.pop();
        KType base = stack.pop();

        if(base.canCastTo(Type.ARRAY) && name.isInt()) {
            base.asArray().set(name.asInt(), val);
        } else {
            base.asObject().put(name.asString(), val);
        }
    }

    private void array_add(Frame stack) {
        KType val = stack.pop();
        KType var = stack.pop();

        var.asArray().add(val);
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
