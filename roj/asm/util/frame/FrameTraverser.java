/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: FrameTraverser.java
 */
package roj.asm.util.frame;

import roj.asm.Opcodes;
import roj.asm.cst.CstDynamic;
import roj.asm.cst.CstType;
import roj.asm.struct.insn.*;
import roj.asm.util.ExceptionEntry;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.asm.util.type.NativeType;
import roj.asm.util.type.Type;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.text.CharList;

import java.io.PrintStream;
import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.asm.util.frame.VarType.*;

public final class FrameTraverser {
    public FrameTraverser(String clazz, String parent) {
        this.clazz = clazz;
    }

    private final String clazz;

    final VList stack = new VList(),
                local = new VList();

    VList lastStack, lastLocal;

    CharList sb = new CharList();

    public void init(List<Type> local, boolean isStatic, boolean isConstructor, Frame firstFrame) {
        if(firstFrame == null) {
            firstFrame = Frame.EMPTY;

            this.local.clear();
            this.stack.clear();
            if (!isStatic) {
                this.local.add(isConstructor ? new Var(UNINITIAL_THIS) : obj(local.get(0).owner));
            } else if(isConstructor) {
                throw new IllegalArgumentException();
            }
            for (int i = isStatic ? 0 : 1; i < local.size(); i++) {
                Var v = fromType(local.get(i), sb);
                if (v == null) {
                    throw new IllegalArgumentException("Unexpected VOID at local[" + i + "]");
                }
                this.local.add(v);
                if (v.type == DOUBLE || v.type == LONG)
                    this.local.add(Var.TOP);
                // ... double top ...
            }
        } else {
            this.stack.copyFrom(firstFrame.stacks);
            this.local.copyFrom(firstFrame.locals);
        }
        this.lastStack = firstFrame.stacks;
        this.lastLocal = firstFrame.locals;
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

    private void pushRefArray(String name) {
        stack.add(obj("[" + name));
    }

    private void pushPrimArray(int arrayType) {
        stack.add(obj("[" + (char)ArrayType.byId((byte) arrayType)));
    }

    private static Var obj(String name) {
        return new Var(name);
    }

    private void initialize(Var v, String className) {
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

    private void returnVal(Type type) {
        Var v = fromType(type, sb);
        if (v == null)
            return;
        stack.add(v);
        if (v.type == DOUBLE || v.type == LONG)
            stack.add(Var.TOP);
    }

    private void popup(Type type) {
        Var v = fromType(type, sb);
        if (v == null)
            return;
        pop(v.type);
    }

    private void loadRefArr(Var v) {
        // v = type.ARRAY
        if (v.owner == null || !v.owner.startsWith("["))
            throw new IllegalArgumentException("Not an array: " + v);
        stack.add(obj(v.owner.substring(1)));
    }

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
        if (trace) {
            final PrintStream out = System.out;
            /*out.println("L: ");
            for (int i = 0; i < local.size; i++) {
                out.print("  ");
                out.println(local.at(i));
            }*/
            out.println("S: ");
            for (int i = 0; i < stack.size; i++) {
                out.print("  ");
                out.println(stack.at(i));
            }
            out.println("N:" + node);
        }

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
                checkOnStack(LONG);
                break;
            case FNEG:
                checkOnStack(FLOAT);
                break;
            case DNEG:
                checkOnStack(DOUBLE);
                break;
            case INEG:
            case I2B:
            case I2C:
            case I2S:
                checkOnStack(INT);
                break;
            case ACONST_NULL:
                stack.add(Var.NULL);
                break;
            case ILOAD:
                isVarType(arg, INT);
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
                isVarType(arg, LONG);
            case LCONST_0:
            case LCONST_1:
                stack.add(Var.LONG);
                stack.add(Var.TOP);
                break;
            case FLOAD:
                isVarType(arg, FLOAT);
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                stack.add(Var.FLOAT);
                break;
            case DLOAD:
                isVarType(arg, DOUBLE);
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
                isVarType(arg, REFERENCE);
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
                checkOnStack(INT);
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
                checkOnStack(FLOAT);
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
                loadRefArr(pop(REFERENCE));
                break;
            case ISTORE:
                t1 = pop(INT);
                isVarType(arg, INT);
                local.set(arg, t1);
                break;
            case FSTORE:
                t1 = pop(FLOAT);
                isVarType(arg, FLOAT);
                local.set(arg, t1);
                break;
            case ASTORE:
                t1 = pop(REFERENCE);
                isVarType(arg, REFERENCE);
                local.set(arg, t1);
                break;
            case LSTORE:
                t1 = pop(LONG);
                isVarType(arg, LONG);
                local.set(arg, t1);
                local.set(arg + 1, Var.TOP);
                break;
            case DSTORE:
                t1 = pop(DOUBLE);
                isVarType(arg, DOUBLE);
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
            case FASTORE:
                pop(FLOAT);
                pop(INT);
                final Var fArray = pop(REFERENCE);
                if(!"[F".equals(fArray.owner)) {
                    throw new IllegalStateException("Unable assign " + fArray.owner + " to [F");
                }
                break;
            case AASTORE:
                pop(REFERENCE);
                pop(INT);
                final Var aArray = pop(REFERENCE);
                if('[' != aArray.owner.charAt(0)) {
                    throw new IllegalStateException("Unable assign " + aArray.owner + " to [L<any>");
                }
                break;
            case LASTORE:
                pop(LONG);
                pop(INT);
                final Var lArray = pop(REFERENCE);
                if(!"[J".equals(lArray.owner)) {
                    throw new IllegalStateException("Unable assign " + lArray.owner + " to [J");
                }
                break;
            case DASTORE:
                pop(DOUBLE);
                pop(INT);
                final Var dArray = pop(REFERENCE);
                if(!"[D".equals(dArray.owner)) {
                    throw new IllegalStateException("Unable assign " + dArray.owner + " to [D");
                }
                break;

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
                pop(FLOAT);
                break;
            case IRETURN:
                flag = 1;
                pop(INT);
                break;
            case ATHROW:
            case ARETURN:
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
                pop(LONG);
                flag = 1;
                break;
            case DRETURN:
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
                checkOnStack(LONG);
                break;
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                pop(DOUBLE);
                checkOnStack(DOUBLE);
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
                isVarType(arg, INT);
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
                popRefOr(); // todo ?
            case GETSTATIC:
                returnVal(((FieldInsnNode) node).type);
                break;
            case PUTSTATIC:
            case PUTFIELD:
                // We can do assign check here...
                popup(((FieldInsnNode) node).type);
                if(code == PUTFIELD)
                    popRefOr(); // todo ?
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

                returnVal(inv.returnType());
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

                returnVal(inv.returnType());
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
    private static void isVarType(int id, byte type) {
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

    /*
     * At no point can long or double be operated (as a TOP or using int ops) on individually.
     */
    private void checkOnStack(byte type) {
        Var v = stack.get(stack.size - 1);
        if(v.type != type) {
            if(v.type == TOP) {
                if(stack.get(stack.size - 2).type == type)
                    return;
            }
            throw new IllegalArgumentException("Unable to cast " + v + " to " + VarType.toString(type));
        }
    }

    private void popRefOr() {
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

    public VList getStack() {
        return this.stack;
    }

    private void visitInRange(InsnList list, int i, Map<InsnNode, Pt> fromTo, Pt pt, boolean trace) {
        VList b1 = new VList().copyFrom(local);
        VList b2 = new VList().copyFrom(stack);

        if(trace)
            System.out.println("$ENTRY");
        while (i < list.size()) {
            InsnNode node = list.get(i++);

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

    public Collection<Frame> collect(InsnList list, InsnNode origin, List<ExceptionEntry> exceptionEntries, boolean trace, ToIntMap<InsnNode> pcRev) {
        final List<Frame> frames = new ArrayList<>();

        /**
         * 一个“基本块”（basic block）就是一个方法中的代码最长的直线型一段段代码序列。
         *     “直线型”也就是说代码序列中除了末尾之外不能有控制流（跳转）指令。
         * 一个基本块的开头可以是方法的开头，也可以是某条跳转指令的跳转目标；
         * 一个基本块的结尾可以是方法的末尾，也可以是某条跳转指令（Java中就是goto、if*系列等；invoke*系列的方法调用指令不算在跳转指令中）。
         */

        Map<InsnNode, Pt> byHandler = new MyHashMap<>();
        Map<InsnNode, Pt> byTarget = new MyHashMap<>();

        // todo exception handler
        gatherJumpTarget(list, byHandler, byTarget, exceptionEntries);
        if(trace)
            System.out.println("Target: " + byTarget.values());

        int i = list.indexOf(origin);
        while (i < list.size()) {
            InsnNode node = list.get(i++);

            final Pt pt = byHandler.get(node);
            if(pt != null) {
                if(!pt.done) {
                    pt.local.copyFrom(local);
                    pt.stack.copyFrom(stack);
                    pt.done = true;
                    if(trace)
                        System.out.println("Make pt at " + node);
                } else {
                    if (local.size != pt.local.size) {
                        throw new IllegalStateException();
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
                frames.add(build(node));
            }

            int flg = visitNode(node, trace);
            switch (flg) {
                case 1:
                    // get snapshot
                    if(i < list.size()) {
                        afterJump(list, byTarget, i);
                    }
                    break;
                case 2:
                case 3: {
                    Pt pt2 = byHandler.get(node);
                    if (pt2 == null) {
                        throw new IllegalArgumentException("Unregistered goto");
                    }
                    assert pt2.to.size() == 1;
                    int i1 = list.indexOf(pt2.to.get(0));

                    visitInRange(list, i1, byHandler, pt2, trace);

                    if(flg == 3) {
                        afterJump(list, byTarget, i);
                    }
                }
                break;
                case 4:
                    node = list.get(i);
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
                            throw new IllegalStateException("Unable to wide " + Opcodes.toString0(node.getOpcode()));
                    }
                    break;
            }
        }

        return frames;
    }

    private void afterJump(InsnList list, Map<InsnNode, Pt> byTarget, int i) {
        Pt pt = byTarget.get(list.get(i));
        if (pt == null) {
            throw new IllegalArgumentException("Dead code after " + list.get(i - 1));
        }
        if (!pt.done) {
            System.err.println("Unable to recovery stack: Stack undone!");
        }
        local.copyFrom(pt.local);
        stack.copyFrom(pt.stack);
    }

    private static void gatherJumpTarget(InsnList list, Map<InsnNode, Pt> bySource, Map<InsnNode, Pt> byTarget, List<ExceptionEntry> exceptions) {
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
        for (ExceptionEntry entry : exceptions) {
            final InsnNode node = InsnNode.validate(entry.handler);
            byTarget.put(node, new Pt(Collections.singletonList(node)));
        }
    }

    private static final class Pt {
        boolean done;
        VList local = new VList(),
              stack = new VList();

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