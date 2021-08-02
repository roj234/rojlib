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

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.CstDynamic;
import roj.asm.cst.CstType;
import roj.asm.tree.Clazz;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.NativeType;
import roj.asm.type.Type;
import roj.asm.util.*;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.VarType.*;
/*
 * jsr/jsr_w:
 *  [local + stack].hasAny(type == uninitialized) && throw Error
 */
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class FrameTraverser {
    public static void main(String[] args) throws IOException {
        Clazz clazz = Parser.parse(IOUtil.readFile(new File(args[0])));
        AttrCode code = clazz.methods.get(0).code;
        System.out.println(code);
        code.computeFrames = true;
        code.toByteArray(new ConstantWriter(), new ByteWriter());
    }

    public static boolean PERFORM_ADDITIONAL_CHECK = true;

    public FrameTraverser() {}

    String clazz;

    char returnType;

    final VarList stack = new VarList(),
                local = new VarList();

    VarList lastStack, lastLocal;

    CharList sb = new CharList();

    public void init(MethodNode owner) {
        Frame empty = Frame.EMPTY;
        this.lastStack = empty.stacks;
        this.lastLocal = empty.locals;

        this.local.clear();
        this.stack.clear();

        this.clazz = owner.ownerClass();
        this.returnType = owner.getReturnType().type;

        boolean _init_ = owner.name().equals("<init>");
        if (0 == (owner.accessFlag2() & AccessFlag.STATIC)) { // this
            this.local.add(_init_ ? new Var(UNINITIAL_THIS) : obj(owner.ownerClass()));
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
    }

    // region A

    private void pushRefArray(String name) {
        stack.add(obj("[" + name));
    }

    private void pushPrimArray(int arrayType) {
        stack.add(obj("[" + (char) PrimArrayTypeToNativeType(arrayType)));
    }

    public static byte PrimArrayTypeToNativeType(int id) {
        switch (id) {
            case 4:
                return 'Z';
            case 5:
                return 'C';
            case 6:
                return 'F';
            case 7:
                return 'D';
            case 8:
                return 'B';
            case 9:
                return 'S';
            case 10:
                return 'I';
            case 11:
                return 'J';
        }
        throw new IllegalStateException("Unknown PrimArrayType " + id);
    }

    private void loadInArray(Var v) {
        if (v.owner == null || !v.owner.startsWith("["))
            throw new IllegalArgumentException("Not an array: " + v);
        stack.add(obj(v.owner.substring(1)));
    }

    private static Var obj(String name) {
        return new Var(name);
    }

    private void initialize(Var v, String className) {
        if(v.type == UNINITIAL_THIS) {

        } else {

        }
        v.owner = v.type == UNINITIAL_THIS ? clazz : className;
        v.type = REFERENCE;
    }

    private Var loadRef(int i) {
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

    private Var pop() {
        Var v = stack.get(stack.size - 1);
        stack.pop(1);
        return v;
    }

    /*
     * At no point can long or double be operated (as a TOP or using int ops) on individually.
     */
    private void checkStackTop(byte type) {
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
    private void popRefLike() {
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

    private Var pop(byte type) {
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
                    break;
            }

            throw new IllegalArgumentException("Unable cast " + v + " to " + VarType.toString(type));
        }
        return v;
    }

    private void pop(byte type, int count) {
        for (int i = 0; i < count; i++) {
            pop(type);
        }
    }

    // endregion

    @SuppressWarnings("fallthrough")
    public Frame build(InsnNode node) {
        Frame frame = null;

        sameTest:
        if (local.size == lastLocal.size) {
            final Var[] list1 = local.list;
            final Var[] list2 = lastLocal.list;

            final int size = local.size;
            for (int i = 0; i < size; i++) {
                if (!list1[i].eq(list2[i]))
                    break sameTest;
            }

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
                if(n[i] == null || o[i] == null) {
                    throw new NullPointerException("Unregistered #" + i);
                }

                if(!n[i].eq(o[i])) {
                    break chopAppendTest;
                }
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

        return frame;
    }

    @SuppressWarnings("fallthrough")
    public int visitNode(InsnNode node, boolean trace) {
        Var t1, t2, t3;
        int arg = -1;
        String clazz = null;

        byte code = node.code;
        if (code >= ILOAD && code <= ALOAD_3) {
            arg = NodeHelper.getIndex(node);
            if (code >= ILOAD_0)
                code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
        } else if (code >= ISTORE && code <= ASTORE_3) {
            arg = NodeHelper.getIndex(node);
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
        switch (code) {
            case RETURN:
                checkReturn(NativeType.VOID);
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
                byte type1 = ((LoadConstInsnNode) node).c.type();
                if(type1 == CstType.DYNAMIC)
                    type1 = (byte) NativeType.validate(((CstDynamic)((LoadConstInsnNode) node).c).getDesc().getType().getString().charAt(0));

                switch (type1) {
                    case CstType.INT:
                        stack.add(Var.INT);
                        break;
                    case CstType.LONG:
                        stack.add(Var.LONG);
                        stack.add(Var.TOP);
                        break;
                    case CstType.FLOAT:
                        stack.add(Var.FLOAT);
                        break;
                    case CstType.DOUBLE:
                        stack.add(Var.DOUBLE);
                        stack.add(Var.TOP);
                        break;
                    case CstType.CLASS:
                        stack.add(obj("java/lang/Class"));
                        break;
                    case CstType.STRING:
                        stack.add(obj("java/lang/String"));
                        break;
                    case CstType.METHOD_TYPE:
                        stack.add(obj("java/lang/invoke/MethodType"));
                        break;
                    case CstType.METHOD_HANDLE:
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
                    final Var arr = pop(REFERENCE);
                    String s;
                    switch (code) {
                        case IALOAD:
                            s = "[I";
                            break;
                        case BALOAD:
                            s = "[B".equals(arr.owner) ? "[B" : "[Z";
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
                    if (!s.equals(arr.owner)) {
                        throw new IllegalStateException("Unable assign " + arr.owner + " to " + s);
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
                    final Var arr = pop(REFERENCE);
                    if (!"[J".equals(arr.owner)) {
                        throw new IllegalStateException("Unable assign " + arr.owner + " to [J");
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
                    final Var arr = pop(REFERENCE);
                    if (!"[F".equals(arr.owner)) {
                        throw new IllegalStateException("Unable assign " + arr.owner + " to [F");
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
                    final Var iArray = pop(REFERENCE);
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
                    if (!s.equals(iArray.owner)) {
                        // boolean store
                        if (!iArray.owner.equals("[Z") || s != "[B") {
                            throw new IllegalStateException("Unable assign " + iArray.owner + " to " + s);
                        }
                    }
                }
            break;
            case FASTORE: {
                pop(FLOAT);
                pop(INT);
                final Var fArray = pop(REFERENCE);
                if (!"[F".equals(fArray.owner)) {
                    throw new IllegalStateException("Unable assign " + fArray.owner + " to [F");
                }
                break;
            }
            case AASTORE: {
                pop(REFERENCE);
                pop(INT);
                final Var aArray = pop(REFERENCE);
                if ('[' != aArray.owner.charAt(0)) {
                    throw new IllegalStateException("Unable assign " + aArray.owner + " to [L<any>");
                }
                break;
            }
            case LASTORE: {
                pop(LONG);
                pop(INT);
                final Var lArray = pop(REFERENCE);
                if (!"[J".equals(lArray.owner)) {
                    throw new IllegalStateException("Unable assign " + lArray.owner + " to [J");
                }
                break;
            }
            case DASTORE: {
                pop(DOUBLE);
                pop(INT);
                final Var dArray = pop(REFERENCE);
                if (!"[D".equals(dArray.owner)) {
                    throw new IllegalStateException("Unable assign " + dArray.owner + " to [D");
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
                checkReturn(NativeType.FLOAT);
                pop(FLOAT);
                break;
            case IRETURN:
                flag = 1;
                checkReturn(NativeType.INT);
                pop(INT);
                break;
            case ATHROW:
            case ARETURN:
                checkReturn(NativeType.CLASS);
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
                checkReturn(NativeType.LONG);
                pop(LONG);
                flag = 1;
                break;
            case DRETURN:
                checkReturn(NativeType.DOUBLE);
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
                pushType(((FieldInsnNode) node).type);
                break;
            case PUTSTATIC:
            case PUTFIELD:
                // We can do assign check here...
                popType(((FieldInsnNode) node).type);
                if(code == PUTFIELD)
                    popRefLike();
                break;
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                IInvocationInsnNode inv = ((IInvocationInsnNode) node);

                List<Type> l = inv.parameters();
                for (int i = l.size() - 1; i >= 0; i--) {
                    Type type = l.get(i);
                    pop(typeType(type));
                }

                if (code != Opcodes.INVOKESTATIC) {
                    if (code == Opcodes.INVOKESPECIAL && inv.name().equals("<init>")) {
                        initialize(pop(UNINITIAL), clazz);
                    } else {
                        pop(REFERENCE);
                    }
                }

                pushType(inv.returnType());
            }
            break;
            // no break
            case INVOKEDYNAMIC: {
                IInvocationInsnNode inv = ((IInvocationInsnNode) node);

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

    private void checkReturn(char type) {
        if(!PERFORM_ADDITIONAL_CHECK)
            return;

        switch (returnType) {
            case NativeType.BOOLEAN:
            case NativeType.BYTE:
            case NativeType.CHAR:
            case NativeType.SHORT:
            case NativeType.INT:
                if(type != NativeType.INT)
                    throw new IllegalArgumentException("return type " + type + " did not satisfy method's " + returnType);
                break;
            default:
                if(returnType != type)
                    throw new IllegalArgumentException("return type " + type + " did not satisfy method's " + returnType);
        }

    }

    private static Var fromType(Type type, CharList sb) {
        int arr = type.array;
        if (arr == 0) {
            final int i = ofType(type);
            switch (i) {
                case -1:
                    return null;
                default:
                    return Var.std(i);
                case -2:
                    return obj(type.owner);
            }
        } else {
            sb.clear();
            for (int i = 0; i < arr; i++)
                sb.append('[');
            if (type.owner == null)
                sb.append(type.type);
            else
                sb.append('L').append(type.owner).append(';');
            return obj(sb.toString());
        }
    }

    private void pushType(Type type) {
        Var v = fromType(type, sb);
        if (v == null)
            return;
        stack.add(v);
        if (v.type == DOUBLE || v.type == LONG)
            stack.add(Var.TOP);
    }

    private void popType(Type type) {
        Var v = fromType(type, sb);
        if (v == null)
            return;
        pop(v.type);
    }

    private static byte typeType(Type type) {
        final int t = ofType(type);
        switch (t) {
            case -1:
                throw new IllegalArgumentException("Illegal void in parameter type");
            case -2:
            case -3:
                return REFERENCE;
            default:
                return (byte) t;
        }
    }

    // 变量i是这个类型的吗? stopped: 需要使用LVT检测！
    // No local variable (or pair, type == long or double) can be accessed before assign.
    private static void isVarType(int id, byte type, boolean load) {
        //if(!PERFORM_ADDITIONAL_CHECK)
        //    return;
        /*switch (type) {
            case DOUBLE:
                local.get(id).type == TOP;
                local.get(id + 1).type == DOUBLE;
                break;
            case LONG:
                local.get(id).type == TOP;
                local.get(id + 1).type == LONG;
                break;
            default:
                local.get(id).type == type;
                break;
        }*/
    }

    // endregion

    private void visitInRange(InsnList list, int i, Map<InsnNode, Pt> fromTo, Pt pt, boolean trace) {
        VarList b1 = new VarList().copyFrom(local);
        VarList b2 = new VarList().copyFrom(stack);

        if(trace)
            System.out.println("$ENTRY");
        while (i < list.size()) {
            InsnNode node = list.get(i++);
            if(trace)
                System.out.println(" pVisit " + i);

            int flg = visitNode(node, trace);
            if(flg != 0 && flg != 4)
                break;
        }
        if(trace)
            System.out.println("$ENTRY OUT");


        local.copyFrom(b1);
        stack.copyFrom(b2);

        pt.local.copyFrom(local);
        pt.stack.copyFrom(stack);
        pt.done = true;
        if(trace)
            System.out.println("Write[T] " + pt);
    }

    public Collection<Frame> collect(InsnList list, List<ExceptionEntry> exceptionEntries, boolean trace, ToIntMap<InsnNode> pcRev) {
        final List<Frame> frames = new ArrayList<>();

        /**
         * 一个“基本块”（basic block）就是一个方法中的代码最长的直线型一段段代码序列。
         *     “直线型”也就是说代码序列中除了末尾之外不能有控制流（跳转）指令。
         * 一个基本块的开头可以是方法的开头，也可以是某条跳转指令的跳转目标；
         * 一个基本块的结尾可以是方法的末尾，也可以是某条跳转指令（Java中就是goto、if*系列等；invoke*系列的方法调用指令不算在跳转指令中）。
         */

        Map<InsnNode, Pt> bySource = new MyHashMap<>();
        Map<InsnNode, Pt> byTarget = new MyHashMap<>();

        getBBBegin(list, bySource, byTarget, exceptionEntries);
        if(trace)
            System.out.println("StackMap起始位置: " + byTarget.keySet());

        int i = 0;
        while (i < list.size()) {
            InsnNode node = list.get(i++);

            Pt pt = bySource.get(node);
            if(pt != null) {
                System.out.println("Pt " + pt.to + " at " + node + "(" + i + "), d=" + pt.done);
                if(!pt.done) {
                    pt.local.copyFrom(local);
                    pt.stack.copyFrom(stack);
                    pt.done = true;
                } else {
                    if (local.size != pt.local.size) {
                        throw new IllegalStateException("stack.equals(lastExecutionPath.stack) || throw Error");
                    }

                    Var[] list1 = local.list;
                    Var[] list2 = pt.local.list;

                    int size = local.size;
                    for (int j = 0; j < size; j++) {
                        if (!list1[j].eq(list2[j]))
                            throw new IllegalStateException();
                    }

                    if (stack.size != pt.stack.size) {
                        throw new IllegalStateException();
                    }

                    list1 = stack.list;
                    list2 = pt.stack.list;

                    size = stack.size;
                    for (int j = 0; j < size; j++) {
                        if (!list1[j].eq(list2[j]))
                            throw new IllegalStateException();
                    }

                }
                // create snapshot
            }

            // build frame on target nodes
            if (byTarget.containsKey(node)) {
                pt = byTarget.get(node);

                if(pt != null && !pt.done) {
                    pt.local.copyFrom(local);
                    pt.stack.copyFrom(stack);
                    System.out.println("Pt2 " + pt + " at " + node + "(" + i + "), d=" + pt.done);
                }
                frames.add(build(node));
            }

            /**
             * RETURN: 1
             * GOTO: 2
             * IF: 3
             * WIDE: 4
             */
            int flg = visitNode(node, trace);
            switch (flg) {
                case 1:
                    // make snapshot
                    if(i < list.size())
                        afterJump(list.get(i), byTarget);
                    break;
                case 2:
                case 3: {
                    Pt pt2 = bySource.get(node);
                    if (pt2 == null) {
                        throw new IllegalArgumentException("Unregistered goto");
                    }
                    assert pt2.to.size() == 1;
                    int i1 = list.indexOf(pt2.to.get(0));


                    visitInRange(list, i1, bySource, pt2, trace);

                    //if(flg == 3) {
                        afterJump(list.get(i), byTarget);
                    //}
                }
                break;
                case 4:
                    switch ((node = list.get(i)).getOpcode()) {
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
                            throw new IllegalStateException("Unable wide " + Opcodes.toString0(node.getOpcode()));
                    }
                    break;
            }
        }

        return frames;
    }

    private void afterJump(InsnNode node, Map<InsnNode, Pt> byTarget) {
        Pt pt = byTarget.get(node);
        if (pt == null) {
            throw new IllegalArgumentException("Dead code at " + node);
        }
        System.out.println("VTP " + node + " => " + pt);
        if (!pt.done) {
            System.err.println("PtStack undone: " + pt);
            pt.local.copyFrom(local);
        }
        local.copyFrom(pt.local);
        stack.copyFrom(pt.stack);
    }

    private static void getBBBegin(InsnList list, Map<InsnNode, Pt> bySource, Map<InsnNode, Pt> byTarget, List<ExceptionEntry> exc) {
        int i = 0;
        while (i < list.size()) {
            InsnNode node = list.get(i++);
            if(node.isJumpSource()) {
                if(node.getClass() == SwitchInsnNode.class) {
                    SwitchInsnNode node1 = (SwitchInsnNode) node;
                    List<InsnNode> lst1 = new ArrayList<>();

                    final Pt pt = new Pt(lst1);

                    lst1.add(node = InsnNode.validate(node1.def));
                    byTarget.put(node, pt);

                    for (InsnNode node2 : node1.mapping.values()) {
                        lst1.add(node = InsnNode.validate(node2));
                        byTarget.put(node, pt);
                    }

                    bySource.put(node1, pt);
                } else {
                    GotoInsnNode node1 = (GotoInsnNode) node;

                    final Pt pt = new Pt(Collections.singletonList(node = InsnNode.validate(node1.getTarget())));
                    byTarget.put(node, pt);
                    bySource.put(node1, pt);
                }
            }
        }
        for (i = 0; i < exc.size(); i++) {
            ExceptionEntry entry = exc.get(i);
            InsnNode node = InsnNode.validate(entry.handler);
            Pt pt = new Pt(Collections.singletonList(node));
            pt.stack.add(obj(entry.type == ExceptionEntry.ANY_TYPE ? "java/lang/Throwable" : entry.type));
            byTarget.put(node, pt);
        }
        for (Map.Entry<InsnNode, Pt> entry : bySource.entrySet()) {
            entry.setValue(byTarget.getOrDefault(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<InsnNode, Pt> entry : byTarget.entrySet()) {
            entry.setValue(bySource.getOrDefault(entry.getKey(), entry.getValue()));
        }
    }

    private static final class Pt {
        boolean done;
        VarList local = new VarList(),
              stack = new VarList();

        final List<InsnNode> to;

        private Pt(List<InsnNode> to) {
            this.to = to;
        }

        @Override
        public String toString() {
            return "Partial{target=" + to + ", stack=" + stack + '}';
        }
    }
}