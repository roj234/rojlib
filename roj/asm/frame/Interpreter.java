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
import roj.asm.util.AccessFlag;
import roj.asm.util.ExceptionEntry;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.*;
import roj.collect.Unioner.Region;
import roj.collect.Unioner.Section;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.VarType.*;
/*
 * jsr/jsr_w:
 *  [local + stack].hasAny(type == uninitialized) && throw Error
 */
/**
 * "Interpreter"
 *
 * @author Roj234
 * @version 1.1
 * @since 2021/6/18 9:51
 */
public final class Interpreter {
    public static void main(String[] args) throws IOException {
        Clazz clazz = Parser.parse(IOUtil.readFile(new File(args[0])));
        AttrCode code = clazz.methods.get(args.length == 1 ? 0 : Integer.parseInt(args[1])).code;
        code.computeFrames = true;

        ClassDefiner.INSTANCE.defineClass(clazz.name.replace('/', '.'), Parser.toByteArray(clazz)).getDeclaredMethods();
    }

    public Interpreter() {}

    String clazz;

    char returnType;

    final VarList stack = new VarList(),
                local = new VarList();

    VarList lastStack, lastLocal;
    public int maxStackSize, maxLocalSize;

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
                if(!n[i].eq(o[i])) {
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
            case ARETURN:
                checkReturn(NativeType.CLASS);
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
    private void isVarType(int id, byte type, boolean load) {
        switch (type) {
            case DOUBLE:
            case LONG:
                id++;
                break;
        }
        if(load && local.size < id)
            throw new IllegalArgumentException("Access #" + id + " before assign");
        maxLocalSize = Math.max(maxLocalSize, id);
    }

    // endregion

    static class Exc implements Section {
        int start, end;
        BasicBlock bb;

        @Override
        public int startPos() {
            return start;
        }

        @Override
        public int endPos() {
            return end;
        }
    }

    public List<Frame> collect(InsnList list, List<ExceptionEntry> exceptionEntries, ToIntMap<InsnNode> pcRev) {
        Map<InsnNode, BasicBlock> bySource = new MyHashMap<>();
        Map<InsnNode, List<BasicBlock>> byTarget = new MyHashMap<>();
        Unioner<Exc> byException = new Unioner<>();

        int i = 0;
        IntList il = new IntList(4);
        while (i < list.size()) {
            InsnNode node = list.get(i++);
            if (node.isJumpSource()) {
                if (node.getClass() == SwitchInsnNode.class) {
                    SwitchInsnNode node1 = (SwitchInsnNode) node;

                    BasicBlock pt = new BasicBlock(i - 1);

                    List<InsnNode> lst1 = new ArrayList<>();
                    il.add(list.indexOf(node = InsnNode.validate(node1.def)));
                    byTarget.computeIfAbsent(node, Helpers.fnArrayList()).add(pt);

                    for (InsnNode node2 : node1.mapping.values()) {
                        il.add(list.indexOf(node = InsnNode.validate(node2)));
                        byTarget.computeIfAbsent(node, Helpers.fnArrayList()).add(pt);
                    }

                    pt.targets = il.toArray();
                    bySource.put(node1, pt);
                } else {
                    GotoInsnNode node1 = (GotoInsnNode) node;

                    BasicBlock pt = new BasicBlock(i - 1);
                    il.add(list.indexOf(node = InsnNode.validate(node1.getTarget())));
                    if(node1 instanceof IfInsnNode) {
                        il.add(i);
                    }
                    pt.targets = il.toArray();

                    byTarget.computeIfAbsent(node, Helpers.fnArrayList()).add(pt);
                    bySource.put(node1, pt);
                }
                il.clear();
            }
        }
        for (i = 0; i < exceptionEntries.size(); i++) {
            ExceptionEntry entry = exceptionEntries.get(i);
            Exc exc = new Exc();
            exc.start = list.indexOf(InsnNode.validate(entry.start));
            exc.end = list.indexOf(InsnNode.validate(entry.end));
            byException.add(exc);

            InsnNode node = InsnNode.validate(entry.handler);
            BasicBlock pt = exc.bb = new BasicBlock(-1);
            pt.targets = new int[] { list.indexOf(node) };
            byTarget.computeIfAbsent(node, Helpers.fnArrayList()).add(pt);
            pt.stackBegin.add(obj(entry.type == ExceptionEntry.ANY_TYPE ? "java/lang/Throwable" : entry.type));
        }

        LongBitSet routines = new LongBitSet();

        MyHashSet<BasicBlock> visited = new MyHashSet<>();
        MyHashSet<BasicBlock> toVisit = new MyHashSet<>();
        MyHashSet<BasicBlock> tmp = new MyHashSet<>();
        BasicBlock first = new BasicBlock(0);
        first.targets = new int[] {0};
        first.localBegin = new VarList().copyFrom(local);
        first.stackBegin = new VarList().copyFrom(stack);
        toVisit.add(first);

        InsnNode node;
        while (!toVisit.isEmpty()) {
            for (BasicBlock bb : toVisit) {
                mainCyc:
                for (int j : bb.targets) {
                    if(!routines.add(j)) continue;

                    local.copyFrom(bb.localBegin);
                    stack.copyFrom(bb.stackBegin);

                    Region rg = byException.findRegion(j);
                    while (j < list.size()) {
                        maxStackSize = Math.max(maxStackSize, stack.size);
                        int flg;
                        switch (flg = visitNode(node = list.get(j++))) {
                            case 1:
                            case 2:
                            case 3:
                                BasicBlock next = bySource.get(node);
                                if (next != null) {
                                    if(visited.add(next)) {
                                        next.localBegin.copyFrom(local);
                                        next.stackBegin.copyFrom(stack);
                                        tmp.add(next);
                                    } else {
                                        if (!next.localBegin.sw(local) || !next.stackBegin.eq(stack)) {
                                            throw new RuntimeException(
                                                    "从各点到达同一位置的跳转栈必须相同！\n" +
                                                            "Block: " + next + "\n" +
                                                            "ExcL: " + next.localBegin + "\n" +
                                                            "ExcS: " + next.stackBegin + "\n" +
                                                            "GotL: " + local + "\n" +
                                                            "GotS: " + stack);
                                        }
                                        next.localBegin.removeTo(local.size);
                                    }
                                } else {
                                    assert flg == 1;
                                }
                                // end of basic block
                                continue mainCyc;
                            case 4:
                                checkWide(list.get(j));
                                break;
                        }
                        if(rg != (rg = byException.findRegion(j))) {
                            List<Exc> mv = rg._int_mod_value();
                            if(mv.isEmpty()) continue;
                            BasicBlock next = mv.get(mv.size() - 1).bb;
                            if(visited.add(next)) {
                                next.localBegin.copyFrom(local);
                                tmp.add(next);
                                System.out.println(next);
                            } else {
                                if (!next.localBegin.sw(local)) {
                                    throw new RuntimeException(
                                            "[Ex]从各点到达同一位置的跳转栈必须相同！\n" +
                                                    "Block: " + next + "\n" +
                                                    "ExcL: " + next.localBegin + "\n" +
                                                    "GotL: " + local);
                                }
                                next.localBegin.removeTo(local.size);
                            }
                        }
                    }
                }
            }
            MyHashSet<BasicBlock> tmp1 = tmp;
            tmp = toVisit;
            tmp.clear();
            toVisit = tmp1;
        }

        List<InsnNode> frames0 = new ArrayList<>(byTarget.keySet());
        frames0.sort((o1, o2) -> Integer.compare(pcRev.getInt(o1), pcRev.getInt(o2)));

        for (i = 0; i < frames0.size(); i++) {
            List<BasicBlock> bbs = byTarget.get(frames0.get(i));
            BasicBlock bb = bbs.get(0);

            //System.out.println("Node: " + frames0.get(i));
            for (int j = 0; j < bbs.size(); j++) {
                BasicBlock next = bbs.get(j);

                //System.out.println(next);
                if(j > 0) {
                    if (!next.localBegin.sw(bb.localBegin) || !next.stackBegin.eq(bb.stackBegin)) {
                        throw new RuntimeException(
                                "从各点到达同一位置的跳转栈必须相同！\n" +
                                        "Block: " + next + "\n" +
                                        "ExcL: " + next.localBegin + "\n" +
                                        "ExcS: " + next.stackBegin + "\n" +
                                        "GotL: " + bb.localBegin + "\n" +
                                        "GotS: " + bb.stackBegin);
                    }
                    bb.localBegin.removeTo(next.localBegin.size);
                }

                if(next.start == -1)
                    continue;
                List<BasicBlock> new1 = byTarget.get(list.get(next.start));
                if(new1 != null) {
                    for (int k = 0; k < new1.size(); k++) {
                        BasicBlock new2 = new1.get(k);
                        if(!bbs.contains(new2)) {
                            bbs.add(new2);
                        }
                    }
                }
            }
        }

        lastLocal = first.localBegin;
        lastStack = first.stackBegin;
        for (i = 0; i < frames0.size(); i++) {
            BasicBlock bb = byTarget.get(frames0.get(i)).get(0);
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
        System.out.println("Max Stack Size: " + maxStackSize + ", Max Local Size: " + maxLocalSize);
        System.out.println(frames0);
        return Helpers.cast(frames0);
    }

    // todo ?
    private int fixLastFrame(int j, InsnList list, int max) {
        int arg = -1;
        while (j < list.size()) {
            InsnNode node = list.get(j++);

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

    private static void checkWide(InsnNode node) {
        switch (node.getOpcode()) {
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
    }
}