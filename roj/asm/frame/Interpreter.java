/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package roj.asm.frame;

import roj.asm.OpcodeUtil;
import roj.asm.cst.Constant;
import roj.asm.cst.CstDynamic;
import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.ExceptionEntry;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.*;
import roj.collect.Unioner.Range;
import roj.collect.Unioner.Region;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.VarType.*;

/**
 * "Interpreter"
 *
 * @author Roj234
 * @version 1.1
 * @since 2021/6/18 9:51
 */
public class Interpreter {
    public Interpreter() {}

    protected String isAssignable(String a, String b) {
        return a;
    }

    String clazz;

    byte returnType;

    final VarList stack = new VarList(),
                local = new VarList();

    VarList lastStack, lastLocal;
    public int maxStackSize, maxLocalSize;

    CharList sb = new CharList();

    public void init(MethodNode owner) {
        Frame empty = Frame.EMPTY;
        this.first = empty;
        this.lastStack = empty.stacks;
        this.lastLocal = empty.locals;

        this.local.clear();
        this.stack.clear();

        this.clazz = owner.ownerClass();

        String desc = owner.rawDesc();
        this.returnType = (byte) desc.charAt(desc.lastIndexOf(')') + 1);

        boolean _init_ = owner.name().equals("<init>");
        if (0 == (owner.accessFlag() & AccessFlag.STATIC)) { // this
            this.local.add(_init_ ? new Var() : obj(owner.ownerClass()));
        } else if(_init_) {
            throw new IllegalArgumentException("static <init>!");
        }

        List<Type> types = owner.parameters();
        for (int i = 0; i < types.size(); i++) {
            Var v = fromType(types.get(i), sb);
            if (v == null) {
                throw new IllegalArgumentException("Unexpected VOID at param[" + i + "]");
            }
            this.local.add(v);
            if (v.type == DOUBLE || v.type == LONG)
                this.local.add(Var.TOP);
            // ... double top ...
        }

        this.maxLocalSize = this.local.size;
        this.maxStackSize = 0;
    }

    // region A

    final void pushRefArray(String name) {
        stack.add(obj(name.endsWith(";") ? "[" + name : "[L" + name + ';'));
    }

    final void pushPrimArray(int arrayType) {
        stack.add(obj("[" + (char) NodeHelper.PrimitiveArray2Type(arrayType)));
    }

    final void loadInArray(Var v) {
        if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
        if (!v.owner.startsWith("["))
            throw new IllegalArgumentException("Not an array: " + v);
        String s;
        if (v.owner.charAt(1) == 'L' && v.owner.charAt(v.owner.length() - 1) == ';') {
            s = v.owner.substring(2, v.owner.length() - 1);
        } else {
            s = v.owner.substring(1);
        }
        stack.add(obj(s));
    }

    static Var obj(String name) {
        return new Var(name);
    }

    final void initialize(Var v, String className) {
        Var v2 = new Var(v.type == UNINITIAL_THIS ? clazz : className);

        Var[] list = stack.list;
        for (int i = stack.size - 1; i >= 0; i--) {
            Var v1 = list[i];
            if(v1 == v) {
                list[i] = v2;
            }
        }
        list = local.list;
        for (int i = local.size - 1; i >= 0; i--) {
            Var v1 = list[i];
            if(v1 == v) {
                list[i] = v2;
            }
        }
    }

    final Var loadRef(int i) {
        Var v = local.get(i);
        switch (v.type) {
            case REFERENCE:
            case UNINITIAL:
            case UNINITIAL_THIS:
            case NULL:
                return v;
            default:
                throw new IllegalArgumentException("#" + i + " is not reference: " + v);
        }
    }

    final Var pop() {
        Var v = stack.get(stack.size - 1);
        stack.pop(1);
        return v;
    }

    /*
     * At no point can long or double be operated (as a TOP or using int ops) on individually.
     */
    final void checkStackTop(byte type) {
        Var v = stack.get(stack.size - 1);
        if(v.type != type) {
            if(v.type == TOP) {
                if(stack.get(stack.size - 2).type == type)
                    return;
            }
            throw new IllegalArgumentException("Unable to cast " + v + " to " + VarType.toString(type));
        }
    }

    /*
     * Ref-like: REF, NULL and UNINITIAL
     */
    final void popRefLike() {
        Var v = stack.get(stack.size - 1);
        stack.pop(1);
        switch (v.type) {
            case REFERENCE:
            case UNINITIAL_THIS:
            case NULL:
                break;
            default:
                throw new IllegalArgumentException("Unable to cast " + v + " to " + VarType.toString(REFERENCE));
        }
    }

    final Var pop(byte type) {
        switch (type) {
            case DOUBLE:
            case LONG:
                pop(TOP);
        }

        Var v = stack.get(stack.size - 1);
        stack.pop(1);
        if(v.type != type) {
            switch (type) {
                case UNINITIAL:
                    if (v.type == UNINITIAL_THIS) {
                        return v;
                    }
                    break;
                case REFERENCE:
                    if (v.type == NULL) {
                        return v;
                    }
                    // kotlin不为人子！
                    if (v.type == UNINITIAL || v.type == UNINITIAL_THIS) {
                        return v;
                    }
                    break;
            }

            throw new IllegalArgumentException("Unable cast " + v + " to " + VarType.toString(type));
        }
        return v;
    }

    final void pop(byte type, int count) {
        for (int i = 0; i < count; i++) {
            pop(type);
        }
    }

    // endregion

    @SuppressWarnings("fallthrough")
    public Frame build(InsnNode node) {
        Frame frame = null;

        local.minus();
        if (local.eq(lastLocal)) {
            switch (stack.size) {
                case 0:
                    frame = new Frame(FrameType.same);
                    break;
                case 2:
                    if(stack.get(1).type != TOP)
                        break;
                case 1:
                    frame = new Frame(FrameType.same_local_1_stack);
                    break;
                default:
                    // full
            }
        }

        chopAppendTest:
        if(frame == null && stack.size == 0) {
            // chop or append;

            int delta = local.size - lastLocal.size;
            int inCommon = Math.min(local.size, lastLocal.size);

            final Var[] n = local.list;
            final Var[] o = lastLocal.list;
            for (int i = 0; i < inCommon; i++) {
                if(n[i] == null || !n[i].eq(o[i])) {
                    break chopAppendTest;
                }
                if(n[i].type == TOP)
                    if(delta < 0) delta++;
                    else delta--;
            }

            if(delta < 0 && delta >= -3) { // chop
                // -1, -2, -3
                frame = new Frame(FrameType.chop);
            } else if (delta > 0 && delta <= 3) { // append
                // 1, 2, 3
                frame = new Frame(FrameType.append);
            }
        }

        if(frame == null) {
            frame = new Frame(FrameType.full);
        }

        (lastLocal = frame.locals).copyFrom(local);
        (lastStack = frame.stacks).copyFrom(stack);
        frame.target = node;
        frame.pack();

        return frame;
    }

    @SuppressWarnings("fallthrough")
    public int visitNode(InsnNode node) {
        int arg = -1;
        String clazz = null;

        byte code = node.code;
        if (code >= ILOAD && code <= ALOAD_3) {
            arg = NodeHelper.getVarId(node);
            if (code >= ILOAD_0)
                code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
        } else if (code >= ISTORE && code <= ASTORE_3) {
            arg = NodeHelper.getVarId(node);
            if (code >= ISTORE_0)
                code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
        } else {
            if (node instanceof IIndexInsnNode) {
                arg = ((IIndexInsnNode) node).getIndex();
            }
            if (node instanceof IClassInsnNode) {
                clazz = ((IClassInsnNode) node).owner();
            }
        }

        int flag = 0;
        Var t1, t2, t3;
        switch (code) {
            case RETURN:
                checkReturn(Type.VOID);
                flag = 1;
                break;
            case GOTO:
                flag = 3;
                break;
            case NOP:
                break;
            case WIDE:
                flag = 4;
                break;
            case LNEG:
                checkStackTop(LONG);
                break;
            case FNEG:
                checkStackTop(FLOAT);
                break;
            case DNEG:
                checkStackTop(DOUBLE);
                break;
            case INEG:
            case I2B:
            case I2C:
            case I2S:
                checkStackTop(INT);
                break;
            case ACONST_NULL:
                stack.add(Var.NULL);
                break;
            case ILOAD:
                isVarType(arg, INT, true);
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case BIPUSH:
            case SIPUSH:
                stack.add(Var.INT);
                break;
            case LLOAD:
                isVarType(arg, LONG, true);
            case LCONST_0:
            case LCONST_1:
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case FLOAD:
                isVarType(arg, FLOAT, true);
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                stack.add(Var.FLOAT);
                break;
            case DLOAD:
                isVarType(arg, DOUBLE, true);
            case DCONST_0:
            case DCONST_1:
                stack.add(Var.DOUBLE);
                stack.add(Var.TOP);
                break;
            case LDC:
            case LDC_W:
            case LDC2_W:
                byte type1 = ((LdcInsnNode) node).c.type();
                if(type1 == Constant.DYNAMIC)
                    type1 = Type.validate(((CstDynamic)((LdcInsnNode) node).c).getDesc().getType().getString().charAt(0));

                switch (type1) {
                    case Constant.INT:
                        stack.add(Var.INT);
                        break;
                    case Constant.LONG:
                        stack.add(Var.LONG);
                        stack.add(Var.TOP);
                        break;
                    case Constant.FLOAT:
                        stack.add(Var.FLOAT);
                        break;
                    case Constant.DOUBLE:
                        stack.add(Var.DOUBLE);
                        stack.add(Var.TOP);
                        break;
                    case Constant.CLASS:
                        stack.add(obj("java/lang/Class"));
                        break;
                    case Constant.STRING:
                        stack.add(obj("java/lang/String"));
                        break;
                    case Constant.METHOD_TYPE:
                        stack.add(obj("java/lang/invoke/MethodType"));
                        break;
                    case Constant.METHOD_HANDLE:
                        stack.add(obj("java/lang/invoke/MethodHandle"));
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown constant: " + node);
                }
                break;
            case ALOAD:
                isVarType(arg, REFERENCE, true);
                stack.add(loadRef(arg));
                break;
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case IAND:
            case IOR:
            case IXOR:
            case ISHL:
            case ISHR:
            case IUSHR:
                pop(INT);
                checkStackTop(INT);
                break;
            case IALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
                pop(INT);
                {
                    final Var v = pop(REFERENCE);
                    String s;
                    switch (code) {
                        case IALOAD:
                            s = "[I";
                            break;
                        case BALOAD:
                            s = "[B".equals(v.owner) ? "[B" : "[Z";
                            break;
                        case CALOAD:
                            s = "[C";
                            break;
                        case SALOAD:
                            s = "[S";
                            break;
                        default:
                            s = null;
                    }
                    if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                    if (!s.equals(v.owner)) {
                        throw new IllegalStateException("Unable assign " + v.owner + " to " + s);
                    }
                }
                stack.add(Var.INT);
                break;
            case L2I:
                pop(LONG);
                stack.add(Var.INT);
                break;
            case D2I:
                pop(DOUBLE);
                stack.add(Var.INT);
                break;
            case FCMPL:
            case FCMPG:
                pop(FLOAT, 2);
                stack.add(Var.INT);
                break;
            case LALOAD:
                pop(INT);
                {
                    Var v = pop(REFERENCE);
                    if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                    if (!"[J".equals(v.owner)) {
                        throw new IllegalStateException("Unable assign " + v.owner + " to [J");
                    }
                }
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case D2L:
                pop(DOUBLE);
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case FALOAD:
                pop(INT);
                {
                    Var v = pop(REFERENCE);
                    if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                    if (!"[F".equals(v.owner)) {
                        throw new IllegalStateException("Unable assign " + v.owner + " to [F");
                    }
                }
                stack.add(Var.FLOAT);
                break;
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                pop(FLOAT);
                checkStackTop(FLOAT);
                break;
            case L2F:
                pop(LONG);
                stack.add(Var.FLOAT);
                break;
            case D2F:
                pop(DOUBLE);
                stack.add(Var.FLOAT);
                break;
            case DALOAD:
                pop(INT);
                {
                    final Var arr = pop(REFERENCE);
                    if (arr.owner == null) throw PathClosedException.NULL_ARRAY;
                    if (!"[D".equals(arr.owner)) {
                        throw new IllegalStateException("Unable assign " + arr.owner + " to [D");
                    }
                }
                stack.add(Var.DOUBLE);
                stack.add(Var.TOP);
                break;
            case L2D:
                pop(LONG);
                stack.add(Var.DOUBLE);
                stack.add(Var.TOP);
                break;
            case AALOAD:
                pop(INT);
                loadInArray(pop(REFERENCE));
                break;
            case ISTORE:
                t1 = pop(INT);
                isVarType(arg, INT, false);
                local.set(arg, t1);
                break;
            case FSTORE:
                t1 = pop(FLOAT);
                isVarType(arg, FLOAT, false);
                local.set(arg, t1);
                break;
            case ASTORE:
                t1 = pop(REFERENCE);
                isVarType(arg, REFERENCE, false);
                local.set(arg, t1);
                break;
            case LSTORE:
                t1 = pop(LONG);
                isVarType(arg, LONG, false);
                local.set(arg, t1);
                local.set(arg + 1, Var.TOP);
                break;
            case DSTORE:
                t1 = pop(DOUBLE);
                isVarType(arg, DOUBLE, false);
                local.set(arg, t1);
                local.set(arg + 1, Var.TOP);
                break;
            case IASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
                pop(INT);
                pop(INT);
                {
                    Var v = pop(REFERENCE);
                    String s;
                    switch (code) {
                        case IASTORE:
                            s = "[I";
                            break;
                        case BASTORE:
                            s = "[B";
                            break;
                        case CASTORE:
                            s = "[C";
                            break;
                        case SASTORE:
                            s = "[S";
                            break;
                        default:
                            s = null;
                    }
                    if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                    if (!s.equals(v.owner)) {
                        // boolean store
                        // noinspection all
                        if (!v.owner.equals("[Z") || s != "[B") {
                            throw new IllegalStateException("Unable assign " + v.owner + " to " + s);
                        }
                    }
                }
            break;
            case FASTORE: {
                pop(FLOAT);
                pop(INT);
                Var v = pop(REFERENCE);
                if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                if (!"[F".equals(v.owner)) {
                    throw new IllegalStateException("Unable assign " + v.owner + " to [F");
                }
                break;
            }
            case AASTORE: {
                pop(REFERENCE);
                pop(INT);
                Var v = pop(REFERENCE);
                if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                if ('[' != v.owner.charAt(0) || (';' != v.owner.charAt(v.owner.length() - 1) && '[' != v.owner.charAt(1))) {
                    throw new IllegalStateException("Unable assign " + v.owner + " to [L<any>");
                }
                break;
            }
            case LASTORE: {
                pop(LONG);
                pop(INT);
                Var v = pop(REFERENCE);
                if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                if (!"[J".equals(v.owner)) {
                    throw new IllegalStateException("Unable assign " + v.owner + " to [J");
                }
                break;
            }
            case DASTORE: {
                pop(DOUBLE);
                pop(INT);
                Var v = pop(REFERENCE);
                if (v.type == NULL) throw PathClosedException.NULL_ARRAY;
                if (!"[D".equals(v.owner)) {
                    throw new IllegalStateException("Unable assign " + v.owner + " to [D");
                }
                break;
            }

            case POP:
                pop();
                break;
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case TABLESWITCH:
            case LOOKUPSWITCH:
                flag = 2;
                pop(INT);
                break;
            case FRETURN:
                flag = 1;
                checkReturn(Type.FLOAT);
                pop(FLOAT);
                break;
            case IRETURN:
                flag = 1;
                checkReturn(Type.INT);
                pop(INT);
                break;
            case ARETURN:
                //checkReturn(Type.CLASS);
            case ATHROW:
                pop(REFERENCE);
                flag = 1;
                break;
            case IFNULL:
            case IFNONNULL:
                flag = 2;
            case MONITORENTER:
            case MONITOREXIT:
                pop(REFERENCE);
                break;
            case IF_icmpeq:
            case IF_icmpne:
            case IF_icmplt:
            case IF_icmpge:
            case IF_icmpgt:
            case IF_icmple:
                pop(INT, 2);
                flag = 2;
                break;
            case IF_acmpeq:
            case IF_acmpne:
                pop(REFERENCE, 2);
                flag = 2;
                break;
            case LRETURN:
                checkReturn(Type.LONG);
                pop(LONG);
                flag = 1;
                break;
            case DRETURN:
                checkReturn(Type.DOUBLE);
                pop(DOUBLE);
                flag = 1;
                break;
            case POP2:
                stack.pop(2);
                break;
            case DUP:
                t1 = pop();
                stack.add(t1);
                stack.add(t1);
                break;
            case DUP_X1:
                t1 = pop();
                t2 = pop();
                stack.add(t1);
                stack.add(t2);
                stack.add(t1);
                break;
            case DUP_X2:
                t1 = pop();
                t2 = pop();
                t3 = pop();
                stack.add(t1);
                stack.add(t3);
                stack.add(t2);
                stack.add(t1);
                break;
            case DUP2:
                t1 = pop();
                t2 = pop();
                stack.add(t2);
                stack.add(t1);
                stack.add(t2);
                stack.add(t1);
                break;
            case DUP2_X1:
                t1 = pop();
                t2 = pop();
                t3 = pop();
                stack.add(t2);
                stack.add(t1);
                stack.add(t3);
                stack.add(t2);
                stack.add(t1);
                break;
            case DUP2_X2:
                t1 = pop();
                t2 = pop();
                t3 = pop();
                Var t4 = pop();
                stack.add(t2);
                stack.add(t1);
                stack.add(t4);
                stack.add(t3);
                stack.add(t2);
                stack.add(t1);
                break;
            case SWAP:
                t1 = pop();
                t2 = pop();
                stack.add(t1);
                stack.add(t2);
                break;
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LAND:
            case LOR:
            case LXOR:
                pop(LONG);
                checkStackTop(LONG);
                break;
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                pop(DOUBLE);
                checkStackTop(DOUBLE);
                break;
            case LSHL:
            case LSHR:
            case LUSHR:
                pop(INT);
                pop(LONG);
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case IINC:
                isVarType(arg, INT, true);
                local.set(arg, Var.INT);
                break;
            case I2L:
                pop(INT);
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case F2L:
                pop(FLOAT);
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case I2F:
                pop(INT);
                stack.add(Var.FLOAT);
                break;
            case I2D:
                pop(INT);
                stack.add(Var.DOUBLE);
                stack.add(Var.TOP);
                break;
            case F2D:
                pop(FLOAT);
                stack.add(Var.DOUBLE);
                stack.add(Var.TOP);
                break;
            case F2I:
                pop(FLOAT);
                stack.add(Var.INT);
                break;
            case ARRAYLENGTH:
            case INSTANCEOF:
                pop(REFERENCE);
                stack.add(Var.INT);
                break;
            case LCMP:
                pop(LONG, 2);
                stack.add(Var.INT);
                break;
            case DCMPL:
            case DCMPG:
                pop(DOUBLE, 2);
                stack.add(Var.INT);
                break;
            case JSR:
            case RET:
                throw new RuntimeException("JSR/RET are not supported.");
            case GETFIELD:
                popRefLike();
            case GETSTATIC:
                pushType(((FieldInsnNode) node).getType());
                break;
            case PUTSTATIC:
            case PUTFIELD:
                // We can do assign check here...
                popType(((FieldInsnNode) node).getType());
                if(code == PUTFIELD)
                    popRefLike();
                break;
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                IInvokeInsnNode inv = ((IInvokeInsnNode) node);

                List<Type> l = inv.parameters();
                for (int i = l.size() - 1; i >= 0; i--) {
                    Type type = l.get(i);
                    pop(typeType(type));
                }

                if (code != INVOKESTATIC) {
                    if (code == INVOKESPECIAL && inv.name.equals("<init>")) {
                        initialize(pop(UNINITIAL), clazz);
                    } else {
                        pop(REFERENCE);
                    }
                }

                pushType(inv.returnType());
            }
            break;
            case INVOKEDYNAMIC: {
                IInvokeInsnNode inv = ((IInvokeInsnNode) node);

                List<Type> l = inv.parameters();
                for (int i = l.size() - 1; i >= 0; i--) {
                    Type type = l.get(i);
                    pop(typeType(type));
                }

                pushType(inv.returnType());
            }
            break;
            case NEW:
                // uninitial
                stack.add(new Var(node));
                break;
            case NEWARRAY:
                pop(INT);
                pushPrimArray(arg);
                break;
            case ANEWARRAY:
                pop(INT);
                pushRefArray(clazz);
                break;
            case CHECKCAST:
                pop(REFERENCE);
                stack.add(obj(clazz));
                break;
            case MULTIANEWARRAY:
                pop(INT, arg);
                stack.add(obj(clazz));
                break;
            default:
                throw new IllegalArgumentException("Unknown opcode " + node);
        }

        return flag;
    }

    // region B

    final void checkReturn(char type) {
        switch (returnType) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                if(type != Type.INT)
                    throw new IllegalArgumentException("return type " + type + " did not satisfy method's " + (char)returnType);
                break;
            default:
                if(returnType != type)
                    throw new IllegalArgumentException("return type " + type + " did not satisfy method's " + (char)returnType);
        }

    }

    static Var fromType(Type type, CharList sb) {
        int arr = type.array;
        if (arr == 0) {
            int i = ofType(type);
            switch (i) {
                case -1: return null;
                default: return Var.std(i);
                case -2: return obj(type.owner);
            }
        } else {
            sb.clear();
            for (int i = 0; i < arr; i++) sb.append('[');
            if (type.owner == null) sb.append((char) type.type);
            else if (type.owner.startsWith("[")) sb.append(type.owner);
            else sb.append('L').append(type.owner).append(';');
            return obj(sb.toString());
        }
    }

    final void pushType(Type type) {
        Var v = fromType(type, sb);
        if (v == null)
            return;
        stack.add(v);
        if (v.type == DOUBLE || v.type == LONG)
            stack.add(Var.TOP);
    }

    final void popType(Type type) {
        Var v = fromType(type, sb);
        if (v == null)
            return;
        pop(v.type);
    }

    static byte typeType(Type type) {
        final int t = ofType(type);
        switch (t) {
            case -1:
                throw new IllegalArgumentException("Illegal void in parameter type");
            case -2:
                return REFERENCE;
            default:
                return (byte) t;
        }
    }

    // 变量i是这个类型的吗? stopped: 需要使用LVT检测！
    final void isVarType(int id, byte type, boolean load) {
        switch (type) {
            case DOUBLE:
            case LONG:
                id++;
                break;
        }
        //if(load && local.size < id)
        //    throw new IllegalArgumentException("Access #" + id + " before assign");
        maxLocalSize = Math.max(maxLocalSize, id + 1);
    }

    // endregion

    static class Exc implements Range {
        int start, end;
        BasicBlock bb;

        @Override
        public long startPos() {
            return start;
        }

        @Override
        public long endPos() {
            return end;
        }
    }

    Map<InsnNode, BasicBlock> bySource = new MyHashMap<>();
    Map<InsnNode, Target> byTarget = new MyHashMap<>();
    Unioner<Exc> byException = new Unioner<>();
    Frame first;

    public Frame getFirst() {
        return first;
    }

    // 算了，没人会帮我的
    protected void checkStackSame(BasicBlock next, VarList localB, VarList StackB) {
//        if (!next.localBegin.sw(localB) || !next.stackBegin.eq(StackB)) {
//            throw new RuntimeException(
//                    "从各点到达同一位置的跳转栈必须相同！\n" +
//                            "Block: " + next + "\n" +
//                            "ExcL: " + next.localBegin + "\n" +
//                            "ExcS: " + next.stackBegin + "\n" +
//                            "GotL: " + local + "\n" +
//                            "GotS: " + stack);
//        }
        if (next.localBegin == null) {
            next.localBegin = new VarList().copyFrom(localB);
        } else {
            next.localBegin.minus(localB);
            localB.minus(next.localBegin);
        }
    }

    public List<Frame> compute(InsnList list, List<ExceptionEntry> exceptionEntries) {
        Map<InsnNode, BasicBlock> bySource = this.bySource; bySource.clear();
        Map<InsnNode, Target> byTarget = this.byTarget; byTarget.clear();
        Unioner<Exc> byException = this.byException; byException.clear();

        collectJumpNodes(list, exceptionEntries, bySource, byTarget, byException);

        MyBitSet routines = new MyBitSet();

        BasicBlock first = new BasicBlock(0);
        first.targets = new int[] {0};
        first.localBegin = new VarList().copyFrom(local);
        first.stackBegin = new VarList().copyFrom(stack);
        MyHashSet<BasicBlock> toVisit = new MyHashSet<>();
        toVisit.add(first);

        MyHashSet<BasicBlock> visited = new MyHashSet<>();
        MyHashSet<BasicBlock> tmp = new MyHashSet<>();
        while (!toVisit.isEmpty()) {
            for (BasicBlock bb : toVisit) {
                mainCyc:
                for (int j : bb.targets) {
                    if(!routines.add(j)) continue;

                    local.copyFrom(bb.localBegin);
                    stack.copyFrom(bb.stackBegin);

                    try {
                        Region rg = byException.findRegion(j);
                        while (j < list.size()) {
                            maxStackSize = Math.max(maxStackSize, stack.size);
                            InsnNode node = list.get(j++);

                            Target target = byTarget.get(node);
                            if (target != null) {
                                if (target.local == null) {
                                    target.local = new VarList().copyFrom(local);
                                    target.stack = new VarList().copyFrom(stack);
                                } else {
                                    target.local.minus(local);
                                    target.stack.minus(stack);
                                }
                            }

                            int flg;
                            switch (flg = visitNode(node)) {
                                case 1:
                                case 2:
                                case 3:
                                    if (bb == first) this.first = build(null);

                                    BasicBlock next = bySource.get(node);
                                    if (next != null) {
                                        if (visited.add(next)) {
                                            next.localBegin.copyFrom(local);
                                            next.stackBegin.copyFrom(stack);
                                            tmp.add(next);
                                        } else {
                                            checkStackSame(next, local, stack);
                                        }
                                    } else {
                                        assert flg == 1;
                                    }
                                    // end of basic block
                                    continue mainCyc;
                                case 4:
                                    checkWide(list.get(j).code);
                                    break;
                            }
                            if (rg != (rg = byException.findRegion(j))) {
                                List<Exc> mv = rg.value();
                                if (mv.isEmpty()) continue;
                                BasicBlock next = mv.get(mv.size() - 1).bb;
                                if (visited.add(next)) {
                                    next.localBegin.copyFrom(local);
                                    //stack.copyFrom(next.stackBegin);
                                    tmp.add(next);
                                } else {
                                    checkStackSame(next, local, null);
                                }
                            }
                        }
                    } catch (PathClosedException ignored) {
                        // 此路不通
                    }
                }
            }
            MyHashSet<BasicBlock> tmp1 = tmp;
            tmp = toVisit;
            tmp.clear();
            toVisit = tmp1;
        }

        List<InsnNode> frames0 = new ArrayList<>(byTarget.keySet());
        frames0.sort((o1, o2) -> Integer.compare(o1.bci, o2.bci));

        int i;
        for (i = 0; i < frames0.size(); i++) {
            Target target = byTarget.get(frames0.get(i));
            List<BasicBlock> bbs = target.bbs;
            BasicBlock bb = bbs.get(0);

            if (target.local != null) {
                checkStackSame(bb, target.local, target.stack);
            } else {
             //   System.err.println(target);
            }

            for (int j = 0; j < bbs.size(); j++) {
                BasicBlock next = bbs.get(j);

                if(j > 0) {
                    checkStackSame(next, bb.localBegin, bb.stackBegin);
                }

                if(next.start < 0) continue;

                Target new1 = byTarget.get(list.get(next.start));
                if(new1 != null) {
                    List<BasicBlock> bbs2 = new1.bbs;
                    for (int k = 0; k < bbs2.size(); k++) {
                        BasicBlock new2 = bbs2.get(k);
                        if(!bbs.contains(new2))
                            bbs.add(new2);
                    }
                }
            }
        }

        lastLocal = first.localBegin;
        lastStack = first.stackBegin;
        for (i = 0; i < frames0.size(); i++) {
            BasicBlock bb = byTarget.get(frames0.get(i)).bbs.get(0);
            assert bb.done;

            local.copyFrom(bb.localBegin);
            stack.copyFrom(bb.stackBegin);
            if(i == frames0.size() - 1) {
                assert bb.targets.length == 1;
                //int max = MathUtils.max(bb.targets);
                local.removeTo(fixLastFrame(bb.targets[0], list, first.localBegin.size));
            }
            frames0.set(i, Helpers.cast(build(frames0.get(i))));
        }
        return Helpers.cast(frames0);
    }

    // todo ?
    @SuppressWarnings("fallthrough")
    private int fixLastFrame(int j, InsnList list, int max) {
        int arg = -1;
        while (j < list.size()) {
            InsnNode node = list.get(j++);

            byte code = node.code;
            if (code >= ILOAD && code <= ALOAD_3) {
                arg = NodeHelper.getVarId(node);
                if (code >= ILOAD_0)
                    code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
            } else if (code >= ISTORE && code <= ASTORE_3) {
                arg = NodeHelper.getVarId(node);
                if (code >= ISTORE_0)
                    code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
            } else {
                if (node instanceof IIndexInsnNode) {
                    arg = ((IIndexInsnNode) node).getIndex();
                }
            }
            switch (code) {
                case DLOAD:
                case LLOAD:
                case LSTORE:
                case DSTORE:
                    arg++;
                case ILOAD:
                case FLOAD:
                case ALOAD:
                case ISTORE:
                case FSTORE:
                case ASTORE:
                    if(arg > max)
                        max = arg;
                    break;
            }
        }
        return max;
    }

    private static void collectJumpNodes(InsnList list,
        List<ExceptionEntry> exceptions,
        Map<InsnNode, BasicBlock> bySource, Map<InsnNode, Target> byTarget, Unioner<Exc> byException) {
        int i = 0;
        IntList il = new IntList(4);
        while (i < list.size()) {
            InsnNode node = list.get(i++);
            if (node.isJumpSource()) {
                if (node.getClass() == SwitchInsnNode.class) {
                    SwitchInsnNode node1 = (SwitchInsnNode) node;

                    BasicBlock pt = new BasicBlock(i - 1);

                    List<InsnNode> lst1 = new ArrayList<>();

                    int id;
                    il.add(id = list.indexOf(node = InsnNode.validate(node1.def)));
                    if (id < 0) throw new IllegalArgumentException(node1 + " (bci:" + (int)node1.bci + ")的默认分支跳转的目标 " +
                                                                       " (bci:" + (int)node.bci + ")无法找到");

                    byTarget.computeIfAbsent(node, fnTarget()).bbs.add(pt);

                    List<SwitchEntry> switcher = node1.switcher;
                    for (int j = 0; j < switcher.size(); j++) {
                        SwitchEntry node2 = switcher.get(j);
                        il.add(id = list.indexOf(node = InsnNode.validate((InsnNode) node2.node)));
                        if (id < 0) throw new IllegalArgumentException(node1 + " (bci:" + (int) node1.bci + ")的分支" + j + "跳转的目标" +
                                                                           " (bci:" + (int) node.bci + ")无法找到");
                        byTarget.computeIfAbsent(node, fnTarget()).bbs.add(pt);
                    }

                    pt.targets = il.toArray();
                    bySource.put(node1, pt);
                } else {
                    GotoInsnNode node1 = (GotoInsnNode) node;

                    BasicBlock pt = new BasicBlock(i - 1);
                    int id;
                    il.add(id = list.indexOf(node = InsnNode.validate(node1.getTarget())));
                    if (id < 0) throw new IllegalArgumentException(node1 + " (bci:" + (int) node1.bci + ")跳转的目标 " +
                                                                       " (bci:" + (int) node.bci + ")无法找到");
                    if (node1 instanceof IfInsnNode) {
                        il.add(i);
                    }
                    pt.targets = il.toArray();

                    byTarget.computeIfAbsent(node, fnTarget()).bbs.add(pt);
                    bySource.put(node1, pt);
                }
                il.clear();
            }
        }
        if (exceptions == null) return;
        for (i = 0; i < exceptions.size(); i++) {
            ExceptionEntry entry = exceptions.get(i);
            Exc exc = new Exc();
            exc.start = list.indexOf(InsnNode.validate(entry.start));
            exc.end = list.indexOf(InsnNode.validate(entry.end));
            byException.add(exc);

            InsnNode node = InsnNode.validate(entry.handler);
            BasicBlock pt = exc.bb = new BasicBlock(-1);
            int id = list.indexOf(node);
            if (id < 0) throw new IllegalArgumentException("异常catcher#" + i + "跳转的目标 " + node + " (bci:" + (int) node.bci + ")无法找到");
            pt.targets = new int[] { id };
            byTarget.computeIfAbsent(node, fnTarget()).bbs.add(pt);
            // noinspection all
            pt.stackBegin.add(obj(entry.type == ExceptionEntry.ANY_TYPE ? "java/lang/Throwable" : entry.type));
        }
    }

    private static <T> T fnTarget() {
        return Helpers.cast((Function<InsnNode, Target>) (n) -> new Target());
    }

    public static void checkWide(byte code) {
        switch (code) {
            case RET:
            case IINC:
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD:
                break;
            default:
                throw new IllegalStateException("Unable wide " + OpcodeUtil.toString0(code));
        }
    }

    static final class Target {
        VarList stack, local;
        final SimpleList<BasicBlock> bbs = new SimpleList<>(2);
    }
}