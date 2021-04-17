package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.kscript.ast.api.TryCatchInfo;
import roj.kscript.type.*;
import roj.kscript.util.ScriptException;
import roj.kscript.util.Stack;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * 标准AST节点 <BR>
 *
 * @author Roj233
 * @since 2020/9/27 13:23
 */
public final class NPASTNode extends Node {
    public NPASTNode(ASTCode code) {
        super(code);
    }

    @Override
    public Node execute(Frame frame) {
        Stack stack = frame.stack;

        switch (getCode()) {
            case ADD_ARRAY:
                array_add(stack);
                break;
            case MOD: {
                KType b = stack.pop();
                KType a = stack.last();

                stack.setLast(KInteger.valueOf(b.isInteger() ? a.asInteger() % b.asInteger() : 0));
            }
            break;
            case XOR:
            case OR:
            case AND: {
                KType b = stack.pop();
                KType a = stack.last();

                boolean val = a.getType() == Type.BOOL;
                if (val) {
                    boolean bool = false;
                    final boolean aa = a.asBoolean(), bb = b.asBoolean();
                    switch (getCode()) {
                        case XOR:
                            bool = aa ^ bb;
                            break;
                        case OR:
                            bool = aa | bb;
                            break;
                        case AND:
                            bool = aa & bb;
                            break;
                    }
                    stack.setLast(KBoolean.valueOf(bool));
                } else {
                    int v = 0;
                    final int aa = a.asInteger(), bb = b.asInteger();
                    switch (getCode()) {
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
                    stack.setLast(KInteger.valueOf(v));
                }
            }
            break;
            case MUL:
            case DIV:
            case SUB:
            case ADD: {
                KType b = stack.pop();
                KType a = stack.last();

                if (!b.isInteger() || !a.isInteger()) {
                    if (getCode() == ASTCode.ADD && (a.isString() || b.isString())) { // string append
                        StringBuilder sb;
                        if (a.getType() != Type.JAVA_OBJECT || !(a.asJavaObject(Object.class).getObject() instanceof StringBuilder)) {
                            stack.setLast(new KJavaObject<>(sb = new StringBuilder(a.asString())));
                        } else {
                            sb = a.asJavaObject(StringBuilder.class).getObject();
                        }
                        sb.append(b.asString());
                        break;
                    }

                    double aa = b.asDouble();
                    double bb = a.asDouble();

                    switch (getCode()) {
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

                    stack.setLast(KDouble.valueOf(aa));
                } else {
                    int aa = b.asInteger();
                    int bb = a.asInteger();

                    switch (getCode()) {
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
                    stack.setLast(KInteger.valueOf(aa));
                }
            }
            break;
            case GET_OBJECT:
                object__get(stack);
                break;
            case PUT_OBJECT:
                object__put(stack);
                break;
            case DUP2: {
                // a,b => a,b , a,b
                KType a = stack.last(1);
                KType b = stack.last();
                stack.push(a);
                stack.push(b);
            }
            break;
            case DUP2_2: {
                // a,b => a,b , a,b
                KType a = stack.last(1);
                KType b = stack.last();
                stack.push(a);
                stack.push(b);
                stack.push(a);
                stack.push(b);
            }
            break;
            case DUP: {
                stack.push(stack.last());
            }
            break;
            case NEGATIVE: {
                KType base = stack.last();
                stack.setLast(KDouble.valueOf(-(base.isInteger() ? base.asInteger() : base.asDouble())));
            }
            break;
            case NOT: {
                KType base = stack.last();
                stack.setLast(KBoolean.valueOf(!base.asBoolean()));
            }
            break;
            case REVERSE: {
                KType base = stack.last();
                base.asInteger();
                stack.setLast(KInteger.valueOf(base.isInteger() ? ~base.asInteger() : 0));
            }
            break;
            case SHIFT_L:
            case SHIFT_R:
            case U_SHIFT_R: {
                KType count = stack.pop();
                KType num = stack.last();

                num.asInteger();
                if (!num.isInteger()) {
                    stack.setLast(KInteger.valueOf(0));
                } else {
                    int val = count.asInteger();

                    int it = num.asInteger();
                    switch (getCode()) {
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
                    stack.setLast(KInteger.valueOf(it));
                }
            }
            break;
            case POP: {
                stack.pop();
            }
            break;
            case LOAD_THIS: {
                stack.push(frame.getThis());
            }
            break;
            case TRY_EXIT: {
                TryCatchInfo info = frame.tryCatch.last();
                if (info.getFin() != null)
                    throw ScriptException.TRY_EXIT;
                return frame.tryCatch.pop().getEnd();
            }
            case RETURN_EMPTY:
                return null;
            case RETURN: {
                frame.result = stack.pop();
                return null;
            }
            case THROW: {
                KType type = stack.pop();
                if(type instanceof KError) {
                    throw type.asKError().getOrigin();
                } else if(type.canCastTo(Type.STRING)) {
                    throw new RuntimeException(type.asString());
                } else {
                    throw new RuntimeException(type.toString());
                }
            }
            case SWAP:
                KType t1 = stack.pop();
                KType t2 = stack.last();
                stack.setLast(t1);
                stack.push(t2);
                break;
            case LOAD_ARGUMENT:
                stack.setLast(frame.getArgs().get(stack.last().asInteger()));
                break;
            case INSTANCE_OF: {
                KType clazz = stack.pop();
                KType instance = stack.last();

                boolean result = instance.canCastTo(Type.OBJECT) && instance.asObject().isInstanceOf(clazz.asObject());
                stack.setLast(KBoolean.valueOf(result));
            }
            break;
            default:
                throw new IllegalArgumentException("Unsupported operator " + getCode());
        }

        return next;
    }

    private void object__get(Stack stack) {
        KType name = stack.pop();
        KType base = stack.last();

        stack.setLast(base.asObject().get(name.asString()));
    }

    private void object__put(Stack stack) {
        KType val = stack.pop();
        KType name = stack.pop();
        KType base = stack.pop();

        base.asObject().put(name.asString(), val);
    }

    private void array_add(Stack stack) {
        KType val = stack.pop();
        KType var = stack.pop();

        var.asArray().add(val);
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
