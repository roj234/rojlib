package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.type.KBool;
import roj.kscript.type.KJavaObject;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.vm.ScriptException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * 标准AST节点 <BR>
 *
 * @author Roj233
 * @since 2020/9/27 13:23
 */
public final class NPASTNode extends Node {
    public NPASTNode(Opcode code) {
        super(code);
    }

    @Override
    @SuppressWarnings("fallthrough")
    public Node execute(Frame f) {
        switch (code) {
            case ADD_ARRAY:
                array_add(f);
                break;
            case MOD: {
                KType b = f.pop();
                KType a = f.last();

                int v = b.isInt() ? a.asInt() % b.asInt() : 0;

                a.setIntValue(v);
            }
            break;
            case XOR:
            case OR:
            case AND: {
                KType b = f.pop();
                KType a = f.last();

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
                    f.setLast(KBool.valueOf(v));
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

                    a.setIntValue(v);
                }
            }
            break;
            case POW:
            case MUL:
            case DIV:
            case SUB:
            case ADD: {
                KType b = f.pop();
                KType a = f.last();

                if (code == Opcode.ADD && (a.isString() || b.isString())) { // string append
                    StringBuilder sb;
                    if (a.getType() != Type.JAVA_OBJECT || !(a.asJavaObject(Object.class).getObject() instanceof StringBuilder)) {
                        f.setLast(new KJavaObject<>(sb = new StringBuilder(a.asString())));
                    } else {
                        sb = a.asJavaObject(StringBuilder.class).getObject();
                    }
                    sb.append(b.asString());
                    break;
                }

                // Number
                if (!b.isInt() || !a.isInt() || code == Opcode.POW) {
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

                    a.setDoubleValue(aa);
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

                    a.setIntValue(aa);
                }
            }
            break;
            case GET_OBJ:
                object__get(f);
                break;
            case PUT_OBJ:
                object__put(f);
                break;
            case DUP2: {
                // a,b => a,b , a,b
                KType a = f.last(1);
                KType b = f.last();
                f.push(a.setFlag(2));
                f.push(b.setFlag(2));
            }
            break;
            case DUP: {
                f.push(f.last().setFlag(2));
            }
            break;
            case NEGATIVE: {
                KType a = f.last();

                if(a.isInt()) {
                    a.setIntValue(-a.asInt());
                } else {
                    a.setDoubleValue(-a.asDouble());
                }
            }
            break;
            case NOT: {
                f.setLast(KBool.valueOf(!f.last().asBool()));
            }
            break;
            case REVERSE: {
                KType a = f.last();
                a.asInt();

                int aa = a.isInt() ? ~a.asInt() : 0;
                a.setIntValue(aa);
            }
            break;
            case SHIFT_L:
            case SHIFT_R:
            case U_SHIFT_R: {
                KType count = f.pop();
                KType num = f.last();

                num.asInt();
                if (!num.isInt()) {
                    num.setIntValue(0);
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

                    num.setIntValue(it);
                }
            }
            break;
            case POP: {
                f.pop();
            }
            break;
            case ARGUMENTS: {
                f.push(f.args);
            }
            break;
            case THIS: {
                f.push(f.$this);
            }
            break;
            case TRY_EXIT: {
                TryNode info = f.tryCatch.last();
                if (info.fin != null)
                    throw ScriptException.TRY_EXIT;
                return f.tryCatch.pop().end;
            }
            case RETURN:
                f.result = f.pop();
            case RETURN_EMPTY:
                return null;
            case THROW: {
                // no pop since stack will be cleared
                throw f.last().asKError().getOrigin();
            }
            case SWAP: {
                int i = f.stackSize - 1;
                if (i < 1) throw new ArrayIndexOutOfBoundsException(i);
                KType[] arr = f.stack;
                KType swp = arr[i];
                arr[i] = arr[i - 1];
                arr[i - 1] = swp;
            }
            break;
            case SWAP3: {
                int i = f.stackSize - 1;
                if(i < 3) throw new ArrayIndexOutOfBoundsException(i);

                // obj idx val val
                //   => val obj idx val

                KType[] arr = f.stack;
                KType swp = arr[i];
                for (int j = 0; j < 3; j++) { // slow move
                    arr[i - j] = arr[i - j - 1];
                }
                arr[i - 3] = swp;
            }
            break;
            case INSTANCE_OF: {
                KType clazz = f.pop();
                KType instance = f.last();

                boolean result = instance.canCastTo(Type.OBJECT) && instance.asObject().isInstanceOf(clazz.asObject());
                f.setLast(KBool.valueOf(result));
            }
            break;
            default:
                throw new IllegalArgumentException("Unsupported op " + code);
        }

        return next;
    }

    private void object__get(Frame stack) {
        KType name = stack.pop();
        KType base = stack.last();

        if(base.canCastTo(Type.ARRAY) && name.isInt()) {
            stack.setLast(base.asArray().get(name.asInt()).setFlag(4));
        } else {
            stack.setLast(base.asObject().get(name.asString()).setFlag(4));
        }
    }

    private void object__put(Frame stack) {
        KType val = stack.pop().setFlag(3);
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
        KType var = stack.pop().setFlag(3);

        var.asArray().add(val);
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
