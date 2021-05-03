/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrCode.java
 * IMPORTANT NOTICE: 在InsnList最后总会有一个 UNTIL_THIS_METHOD_END
 */
package roj.asm.struct.attr;

import roj.asm.Opcodes;
import roj.asm.constant.*;
import roj.asm.struct.IMethodData;
import roj.asm.struct.insn.*;
import roj.asm.util.*;
import roj.asm.util.frame.*;
import roj.asm.util.type.IO;
import roj.asm.util.type.Type;
import roj.collect.*;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.PrimitiveIterator;

import static roj.asm.Opcodes.*;

public class AttrCode extends Attribute {
    public AttrCode(IMethodData method) {
        super("Code");
        this.owner = method;
    }

    public AttrCode(IMethodData method, ByteList list, ConstantPool pool) {
        this(method, new ByteReader(list), pool);
    }

    public AttrCode(IMethodData method, ByteReader list, ConstantPool pool) {
        this(method);
        initialize(pool, list);
    }

    public static final InsnNode METHOD_END_MARK = EndOfInsn.INSTANCE;

    public IMethodData owner;

    public final InsnList instructions = new InsnList();

    public int stackSize, localSize;

    public boolean computeFrames = false;
    // goto 是跳到'那个'位置，也就是'之前'
    public List<Frame> frames;
    public final ArrayList<ExceptionEntry> exceptions = new ArrayList<>();

    public final AttributeList attributes = new AttributeList();

    public void initialize(ConstantPool pool, ByteReader r) {
        this.stackSize = r.readUnsignedShort();
        this.localSize = r.readUnsignedShort();
        IntBiMap<InsnNode> pcCounter = parseCode(pool, r.readBytesDelegated(r.readInt()));
        pcCounter.unmodifiable();

        int largestIndex = 0;
        for (PrimitiveIterator.OfInt itr = pcCounter.selfKeySet().iterator(); itr.hasNext(); ) {
            int i = itr.nextInt();
            if (i > largestIndex) largestIndex = i;
        }
        largestIndex++;

        int len = r.readUnsignedShort();
        exceptions.ensureCapacity(len);
        // start_pc,end_pc,handler_pc中的值都表示的是PC计数器中的指令地址
        //exception_table表示的意思是：
        //如果字节码从第start_pc行到第end_pc行之间出现了catch_type所描述的异常类型，那么将跳转到handler_pc行继续处理。
        // catchType => ConstantClass extends Throwable
        for (int i = 0; i < len; i++) {
            InsnNode start = pcCounter.get(r.readUnsignedShort()),
                    end = pcCounter.get(r.readUnsignedShort()),
                    handler = pcCounter.get(r.readUnsignedShort());
            final ExceptionEntry entry = new ExceptionEntry(start, end, handler, (CstClass) pool.get(r));
            this.exceptions.add(entry);
        }
        instructions.add(METHOD_END_MARK);

        len = r.readUnsignedShort();
        // attributes

        try {
            for (int i = 0; i < len; i++) {
                String name = ((CstUTF) pool.get(r)).getString();
                int end = r.readInt() + r.index;
                switch (name) {
                    case "LineNumberTable":
                        //try {
                        attributes.add(new AttrLineNumber(r, pcCounter));
                        break;
                    case "LocalVariableTable":
                    case "LocalVariableTypeTable": {
                        // LocalVariableTypeTable是可选的
                        attributes.add(new AttrLocalVars(name, pool, r, pcCounter, largestIndex));
                    }
                    break;
                    case "StackMapTable": {
                        this.frames = readStackMap(pool, r, r.readUnsignedShort(), pcCounter);
                    }
                    break;
                    case "Exceptions":
                        attributes.add(new AttrStringList(name, r, pool, 1));
                        break;
                    case "RuntimeInvisibleTypeAnnotations":
                    case "RuntimeVisibleTypeAnnotations":
                        attributes.add(new AttrTypeAnnotation(name, r, pool, this));
                        break;
                    default:
                        System.err.println("[Warning] Skipped unknown attribute " + name + " for class " + (owner != null ? owner.ownerClass() + '.' + owner.name() : "UNKNOWN"));
                }
                if (r.index != end) {
                    System.err.println("[Warning] " + (end - r.index) + " bytes not read correctly!");
                    r.index = end;
                }
            }
        } catch (Throwable e) {
            if(owner == null)
                throw e;
            throw new RuntimeException(owner.ownerClass() + '.' + owner.name() + ": failed to read attribute.", e);
        }
    }

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(this.stackSize).writeShort(this.localSize).writeInt(0);

        final ByteList list = w.list;
        int lenIdx = list.pos();

        ToIntMap<InsnNode> pcRev = reIndex(pool);
        for (int i = 0; i < instructions.size(); i++) {
            InsnNode node = instructions.get(i);
            node.handlePCRev(pcRev);
            node.toByteArray(w);
        }

        int length = list.pos() - (lenIdx);
        list.markAndGo(lenIdx - 4);
        w.writeInt(length).list.back();

        w.writeShort(this.exceptions.size());
        for (ExceptionEntry ex : this.exceptions) {
            w.writeShort(pcRev.getInt(InsnNode.validate(ex.start)))
                    .writeShort(pcRev.getInt(InsnNode.validate(ex.end)))
                    .writeShort(pcRev.getInt(InsnNode.validate(ex.handler)))
                    .writeShort(ex.type == ExceptionEntry.ANY_TYPE ? 0 : pool.getClassId(ex.type));
        }

        int index = list.pos();
        w.writeShort(0);

        final AttributeList attrs = this.attributes;
        int attrLen = attrs.size();
        for (int i = 0; i < attrLen; i++) {
            Attribute attribute = attrs.get(i);
            if(attribute instanceof ICodeAttribute) {
                w.writeShort(pool.getUtfId(attribute.name)).writeInt(-1);

                int index1 = list.pos();
                ((ICodeAttribute) attribute).toByteArray(pool, w, pcRev);
                int plusLen = list.pos() - index1;

                list.markAndGo(index1 - 4);
                w.writeInt(plusLen);
                list.back();
            } else {
                attribute.toByteArray(pool, w);
            }
        }

        if (this.frames != null) {
            w.writeShort(pool.getUtfId("StackMapTable")).writeInt(0);
            int lenIdx1 = list.pos();

            if (computeFrames)
                recalculateFrames(pcRev);

            writeFrames(pool, w.writeShort(frames.size()), pcRev);

            int length1 = list.pos() - lenIdx1;
            list.markAndGo(lenIdx1 - 4);
            w.writeInt(length1).list.back();
            attrLen++;
        }

        list.markAndGo(index);
        w.writeShort(attrLen).list.back();

        instructions.add(METHOD_END_MARK);
    }

    /**
     * 重新计算node的index并预先获得常量池索引
     *
     * @param pool 常量池
     */
    public ToIntMap<InsnNode> reIndex(ConstantWriter pool) {
        final InsnList insn = this.instructions;

        InsnNode last = insn.remove(insn.size() - 1);
        if (last != METHOD_END_MARK) {
            System.err.println(this.toString());
            throw new IllegalArgumentException("Endpoint must be METHOD_END_MARK");
        }

        ByteWriter w = new ByteWriter(new ByteList.EmptyByteList());
        ToIntMap<InsnNode> pcRev = new ToIntMap<InsnNode>(insn.size()) {
            @Override
            public int getInt(InsnNode key) {
                int v = super.getOrDefault(key, -1);
                if(v == -1) {
                    System.out.println("Self " + this);
                    System.out.println("Insn " + AttrCode.this);
                    throw new IllegalArgumentException("Couldn't find bci for " + key);
                }
                return v;
            }

            @Override
            public Integer get(Object id) {
                throw new UnsupportedOperationException("Disabled due to immutable temp var");
            }
        };

        InsnNode node;
        pcRev.putInt(insn.get(0), 0);

        int reccIdx = -1, reccPos = 0;
        int i, j = 0;

        do {
            i = reccIdx + 1;
            reccIdx = -1;
            w.list.pos(reccPos);

            for (int e = insn.size(); i < e; i++) {
                node = insn.get(i);
                pcRev.putInt(node, w.list.pos());
                node.setBci(w.list.pos());

                // 简化的终止条件: 此轮node return false并且长度不变
                if (node.handlePCRev(pcRev) && reccIdx == -1) {
                    reccIdx = i - 1;
                    reccPos = w.list.pos();
                }

                node.preToByteArray(pool, w);
            }

            if(j++ > 5) {
                throw new IllegalArgumentException("Unable to correct bytecode order for " + (owner == null ? "<unknown>" : owner.ownerClass() + '.' + owner.name()));
            }
        } while (reccIdx != -1 && insn.get(reccIdx + 1).handlePCRev(pcRev));

        pcRev.putInt(METHOD_END_MARK, pcRev.getInt(insn.get(insn.size() - 1)) + 1);

        return pcRev;
    }

    /**
     * 重新计算栈帧
     * <pre>
     *     一般编译器碰到一个跳转指令（ifxx/goto等）就会生成一个frame来描述跳转处的locals情况
     * 好，到了链接时校验方法字节码的时候会把方法的所有指令都线性扫一遍，碰到store类指令（istore、fstore等）就会把在init_frame.locals中保存一个type，碰到跳转指令就会把init_frame和StackMapTable中对应offset的frame对比看看locals stack 类型 大小是否一致来判断跳转前后局部变量是否发生变化
     * </pre>
     */
    private void recalculateFrames(ToIntMap<InsnNode> pcRev) {
        this.frames.clear();

        FrameTraverser calculator;
        {
            List<Type> list = new SimpleList<>();
            boolean isStatic = owner.access().contains(AccessFlag.STATIC);
            if (!isStatic) // this
                list.add(new Type(IO.I, owner.ownerClass(), 0));
            List<Type> types = owner.parameters();
            list.addAll(types);

            calculator = new FrameTraverser(owner.ownerClass(), owner.parentClass());
            calculator.init(list, isStatic, owner.name().equals("<init>"), getFirstFrame());
        }

        try {
            frames.addAll(calculator.collect(instructions, getFirstFrame().target, exceptions, true, pcRev));
        } catch (Throwable e) {

            List<Type> list = new SimpleList<>();
            boolean isStatic = owner.access().contains(AccessFlag.STATIC);
            if (!isStatic) // this
                list.add(new Type(IO.I, owner.ownerClass(), 0));
            List<Type> types = owner.parameters();
            list.addAll(types);

            calculator = new FrameTraverser(owner.ownerClass(), owner.parentClass());
            calculator.init(list, isStatic, owner.name().equals("<init>"), getFirstFrame());

            frames.addAll(calculator.collect(instructions, getFirstFrame().target, exceptions, true, pcRev));

        }
        System.out.println("====Method " + owner.name());
        System.out.println(getFirstFrame());
        System.out.println("======================");
        System.out.println(frames);
        System.out.println("===================================");

    }

    protected IntBiMap<InsnNode> parseCode(ConstantPool pool, ByteList data) {
        ByteReader r = new ByteReader(data);
        InsnNode prevNode = null, currNode = null;

        IntBiMap<InsnNode> pci = new IntBiMap<>(data.limit());
        int prevIndex = 0;

        final InsnList insn = this.instructions;
        while (!r.isFinished()) {
            try {
                insn.add(currNode = parse0(pool, r, prevNode));
            } catch (Throwable e) {
                final PrintStream err = System.err;

                err.println("Please report to Roj234 with all messages below.");
                err.println("====================PARSING NODE====================");
                err.println("Prev: " + currNode);
                r.index = prevIndex;
                try {
                    err.println("Except: " + toString0(r.readByte()));
                } catch (Throwable e111) {
                    r.index = prevIndex;
                    err.println("Except: " + r.readByte());
                }
                err.println();
                err.println("PC: " + pci.values());
                err.println("Bytecode: " + r.getBytes());
                err.println("====================PARSING NODE====================");

                throw new InternalError("At " + (owner == null ? "??" : owner.ownerClass() + '.' + owner.name()), e);
            }
            if (currNode != null) {
                currNode.setBci(prevIndex);
                pci.put(prevIndex, currNode);

                prevNode = currNode;
            }
            prevIndex = r.index;
        }

        ListIterator<InsnNode> itr = insn.listIterator();
        while (itr.hasNext()) {
            InsnNode node = itr.next();
            if (node.getClass() == JumpPrimer.class) {
                JumpPrimer n = (JumpPrimer) node;

                int self = pci.getByValue(n);
                validateJump(pci.get(self + n.targetIndex - 1));
                InsnNode target = pci.get(self + n.targetIndex);
                if (target == null) {
                    final PrintStream er = System.err;

                    er.println("====================JUMP TO NULL====================");
                    er.println("Please report to Roj234 with this class and this message.");
                    er.println("Instruction " + n + " jumping to null of #" + n.targetIndex);
                    er.println("PC: " + pci.values());

                    throw new RuntimeException("At " + (owner == null ? "??" : owner.ownerClass() + '.' + owner.name()));
                }

                itr.set(n.bake(target));
            } else if (node.getClass() == SwchPrimer.class) {
                SwchPrimer b = (SwchPrimer) node;
                int self = pci.getByValue(b);

                LinkedIntMap<Object> map = Helpers.cast(b.mapping);
                for (IntMap.Entry<Object> entry : map.entrySet()) {
                    InsnNode n = pci.get(self + (int) entry.getValue());

                    if (n == null) {
                        final PrintStream er = System.err;

                        er.println("====================JUMP TO NULL====================");
                        er.println("Please report to Roj234 with this class and this message.");
                        er.println("Instruction " + b + " jumping to null of #" + (self + (int) entry.getValue()));
                        er.println("PC: " + pci.values());

                        throw new RuntimeException("At " + (owner == null ? "??" : owner.ownerClass() + '.' + owner.name()));
                    }

                    entry.setValue(n);
                }

                InsnNode def = pci.get(self + b.defaultValue);

                if (def == null) {
                    final PrintStream er = System.err;

                    er.println("====================JUMP TO NULL====================");
                    er.println("Please report to Roj234 with this class and this message.");
                    er.println("Instruction " + b + " jumping to null of #" + (self + b.defaultValue));
                    er.println("PC: " + pci.values());

                    throw new RuntimeException("At " + (owner == null ? "??" : owner.ownerClass() + '.' + owner.name()));
                }

                itr.set(new SwitchInsnNode(b.code, def, Helpers.cast(map)));
            }
        }

        FrameTraverser fc;
        {
            List<Type> list = new SimpleList<>();
            boolean isStatic = owner.access().contains(AccessFlag.STATIC);
            if (!isStatic) // this
                list.add(new Type(IO.I, owner.ownerClass(), 0));
            List<Type> types = owner.parameters();
            list.addAll(types);

            fc = new FrameTraverser(owner.ownerClass(), owner.parentClass());
            fc.init(list, isStatic, owner.name().equals("<init>"), null);
        }

        try {
            o:
            for (int i = 0; i < insn.size();) {
                InsnNode node = insn.get(i++);
                int flag = fc.visitNode(node, false);
                switch (flag) {
                    case 1: // return
                    case 2: // goto
                    case 3: // if
                        this.firstFrame = fc.build(null);
                        break o;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            {
                List<Type> list = new SimpleList<>();
                boolean isStatic = owner.access().contains(AccessFlag.STATIC);
                if (!isStatic) // this
                    list.add(new Type(IO.I, owner.ownerClass(), 0));
                List<Type> types = owner.parameters();
                list.addAll(types);

                fc.init(list, isStatic, owner.name().equals("<init>"), null);
            }

            o:
            for (int i = 0; i < insn.size();) {
                InsnNode node = insn.get(i++);
                int flag = fc.visitNode(node, true);
                switch (flag) {
                    case 2: // goto
                    case 3: // if
                        //node = i >= insn.size() ? null : insn.get(i);
                    case 1: // return
                        this.firstFrame = fc.build(null);
                        break o;
                }
            }
        }

        return pci;
    }

    private static void validateJump(InsnNode node) {
        if(node != null && node.getOpcode() == WIDE)
            throw new IllegalArgumentException("Jump target must not \"after\" wide");
    }

    public static InsnNode parse0(ConstantPool pool, ByteReader r, InsnNode prevNode) {
        byte code = Opcodes.byId(r.readByte());

        boolean widen = prevNode != null && prevNode.code == Opcodes.WIDE;
        if(widen) {
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
                    throw new IllegalStateException("Unable to wide " + Opcodes.toString0(code));
            }
        }

        switch (code) {
            case PUTFIELD:
            case GETFIELD:
            case PUTSTATIC:
            case GETSTATIC:
                return new FieldInsnNode(code, (CstRefField) pool.get(r));

            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
                return new InvokeInsnNode(code, (CstRef) pool.get(r));
            case INVOKEINTERFACE:
                return new InvokeItfInsnNode((CstRefItf) pool.get(r), r.readShort());
            case INVOKEDYNAMIC:
                return new InvokeDynInsnNode((CstDynamic) pool.get(r), r.readUnsignedShort());

            case GOTO:


            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_icmpeq:
            case IF_icmpne:
            case IF_icmplt:
            case IF_icmpge:
            case IF_icmpgt:
            case IF_icmple:
            case IF_acmpeq:
            case IF_acmpne:
            case IFNULL:
            case IFNONNULL:
                return new JumpPrimer(code, r.readShort());
            case GOTO_W:
                return new JumpPrimer(code, r.readInt());

            /*  There are some parts of the JVM specification which suggest that
                    the operations JSR (Jump SubRoutine), JSR_W (Jump SubRoutine Wide) and RET (RETurn from subroutine) may be
                    used only up to class file version 50.0 (JDK 1.6):

                3.13 Compiling Finally
            */

            case JSR:
                throwUnsupportedCode();
                return new U2InsnNode(code, r.readShort());
            case JSR_W:
                throwUnsupportedCode();
                return new U4InsnNode(code, r.readInt());
            case RET:
                throwUnsupportedCode();
                return widen ? new U2InsnNode(code, r.readShort()) : new U1InsnNode(code, r.readByte());
            case SIPUSH:
                return new U2InsnNode(code, r.readShort());

            case BIPUSH:
            case NEWARRAY:
                return new U1InsnNode(code, r.readByte());

            case LDC:
                return new LoadConstInsnNode(code, pool.array(r.readUByte()));
            case LDC_W:
            case LDC2_W:
                return new LoadConstInsnNode(code, pool.get(r));

            case IINC:
                return widen ? new IncrInsnNode(r.readUnsignedShort(), r.readShort()) : new IncrInsnNode(r.readUByte(), r.readByte());

            case NEW:
            case ANEWARRAY:
            case INSTANCEOF:
            case CHECKCAST:
                return new ClassInsnNode(code, (CstClass) pool.get(r));

            case MULTIANEWARRAY:
                return new MDArrayInsnNode((CstClass) pool.get(r), r.readUByte());

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
                return widen ? new U2InsnNode(code, r.readUnsignedShort()) : new U1InsnNode(code, r.readUByte());

            case TABLESWITCH:
                return parseTableSwitch(r);
            case LOOKUPSWITCH:
                return parseLookupSwitch(r);
            default:
                return new NPInsnNode(code);
        }

    }

    private static void throwUnsupportedCode() {
        final PrintStream err = System.err;
        err.println("=====================================================");
        err.println("|      JSR or RET was found in your class file      |");
        err.println("|   It's deprecated and only be used for a little.  |");
        err.println("|     So there's NO SOLUTION to rewrite them.       |");
        err.println("=====================================================");
    }

    public static InsnNode parseTableSwitch(ByteReader r) {
        if ((r.index & 3) != 0) {
            r.index = r.index + 4 - (r.index & 3);
        }

        int def = r.readInt();
        int low = r.readInt();
        int hig = r.readInt();
        int count = hig - low + 1;
        int i = 0;

        if(count > 100000)
            throw new IllegalArgumentException("length > 100000");

        LinkedIntMap<Integer> mapping = new LinkedIntMap<>(count + 5);
        while (count > i) {
            mapping.put(i++ + low, r.readInt());
        }
        return new SwchPrimer(Opcodes.TABLESWITCH, def, mapping);
    }

    public static InsnNode parseLookupSwitch(ByteReader r) {
        while (r.index % 4 != 0) {
            r.index++;
        }
        int defaultValue = r.readInt();
        int switchValueCount = r.readInt();
        LinkedIntMap<Integer> mapping = new LinkedIntMap<>(switchValueCount + 5);
        while (switchValueCount > 0) {
            mapping.put(r.readInt(), r.readInt());
            switchValueCount--;
        }
        return new SwchPrimer(Opcodes.LOOKUPSWITCH, defaultValue, mapping);
    }

    @SuppressWarnings("fallthrough")
    protected void writeFrames(ConstantWriter pool, ByteWriter w, ToIntMap<InsnNode> pcRev) {
        Frame prev = getFirstFrame();
        if (prev == null)
            prev = Frame.EMPTY;
        for (Frame curr : frames) {
            int type = curr.type.s;
            int offset = pcRev.getInt(InsnNode.validate(curr.target));
            if (prev.target != null)
                offset -= pcRev.getInt(InsnNode.validate(prev.target)) + 1;
            if (offset > 65535 || offset < 0) {
                if (prev.target != null)
                    System.err.println("Prev bci: " + pcRev.getInt(InsnNode.validate(prev.target)));
                System.err.println("Curr bci: " + pcRev.getInt(InsnNode.validate(curr.target)));

                System.err.println("Prev curr: " + prev);
                System.err.println("Curr curr: " + curr);

                throw new IllegalArgumentException("Illegal curr offset");
            }
            switch (curr.type) {
                case same:
                    if (offset < 64) {
                        type = offset;
                    } else {
                        type = FrameType.same_ex.s;
                        curr.type = FrameType.same_ex;
                    }
                    break;
                case same_local_1_stack:
                    if (offset < 64) {
                        type = offset + 64;
                    } else {
                        type = FrameType.same_local_1_stack_ex.s;
                        curr.type = FrameType.same_local_1_stack_ex;
                    }
                    break;
                case chop:
                    type = 251 - (prev.locals.size - curr.locals.size);
                    break;
                case append:
                    type = 251 + (curr.locals.size - prev.locals.size);
                    break;
                case same_ex:
                case same_local_1_stack_ex:
                case full:
                    break;
            }
            w.writeByte((byte) type);
            switch (curr.type) {
                case same:
                    break;
                case same_local_1_stack_ex:
                    w.writeShort(offset);
                case same_local_1_stack:
                    writeVType(curr.stacks.get(0), w, pool, pcRev);
                    break;
                case chop:
                    // 251 - type
                case same_ex:
                    w.writeShort(offset);
                    break;
                case append:
                    w.writeShort(offset);
                    //writeVerify(curr.stacks.get(0), w, pool);
                    for (int i = curr.locals.size + 251 - type, e = curr.locals.size; i < e; i++) {
                        writeVType(curr.locals.get(i), w, pool, pcRev);
                    }
                    break;
                case full:
                    w.writeShort(offset)
                            .writeShort(curr.locals.size);
                    for (int i = 0, e = curr.locals.size; i < e; i++) {
                        writeVType(curr.locals.get(i), w, pool, pcRev);
                    }

                    w.writeShort(curr.stacks.size);
                    for (int i = 0, e = curr.stacks.size; i < e; i++) {
                        writeVType(curr.stacks.get(i), w, pool, pcRev);
                    }
            }

            prev = curr;
        }
    }

    private static void writeVType(Var v, ByteWriter w, ConstantWriter pool, ToIntMap<InsnNode> pcRev) {
        w.writeByte(v.type);
        switch (v.type) {
            case VarType.REFERENCE:
                w.writeShort(pool.getClassId(v.owner));
                break;
            case VarType.UNINITIAL:
                w.writeShort(pcRev.getInt(InsnNode.validate(v.node)));
                break;
        }
    }

    protected Frame firstFrame;

    public Frame getFirstFrame() {
        return firstFrame;
    }

    protected List<Frame> readStackMap(ConstantPool pool, ByteReader r, int tableLen, IntBiMap<InsnNode> pcCounter) {
        List<Frame> list = new SimpleList<>(tableLen);

        Frame prev = getFirstFrame(), curr;

        int allOffset = -1;
        for (int k = 0; k < tableLen; k++) {

            int type = r.readUByte();
            curr = new Frame(FrameType.byId(type));

            if (prev != null)
                curr.locals.copyFrom(prev.locals); // copy

            int offset = -1;
            switch (curr.type) {
                case same:
                    offset = type;
                    // 变量 相同
                    // 栈 空
                    break;
                case same_ex:
                    offset = r.readUnsignedShort();
                    // 同上
                    break;
                case same_local_1_stack:
                    offset = type - 64;
                    curr.stacks.removeTo(1);
                    curr.stacks.set(0, getVType(pool, r, pcCounter));
                    // 变量 相同
                    // 栈 一个
                    break;
                case same_local_1_stack_ex:
                    offset = r.readUnsignedShort();
                    curr.stacks.removeTo(1);
                    curr.stacks.set(0, getVType(pool, r, pcCounter));
                    // 同上
                    break;
                case chop:
                    offset = r.readUnsignedShort();
                    if (curr.locals.size < 251 - type) {
                        throw new IllegalStateException(owner.ownerClass() + '.' + owner.name() + ": Chop(" + (251 - type) + "): too few: " + curr.locals);
                    }
                    curr.locals.pop(251 - type);
                    // 变量 k个没了
                    // 栈 空
                    break;
                case append: {
                    offset = r.readUnsignedShort();
                    int count = type - 251;
                    while (count > 0) {
                        count--;
                        curr.locals.add(getVType(pool, r, pcCounter));
                    }
                    if (count < 0) {
                        System.out.println(curr);
                        throw new IllegalStateException(String.valueOf(count));
                    }
                    // 变量 增加 k 个
                    // 栈 空
                }
                break;
                case full: {
                    offset = r.readUnsignedShort();
                    int count = r.readUnsignedShort();

                    curr.locals.clear();
                    curr.stacks.clear();

                    if (count > localSize)
                        throw new IllegalStateException(owner.ownerClass() + '.' + owner.name() + ": Frame local (" + count + ") > maximum local variable size (" + localSize + ")");

                    while (count > 0) {
                        count--;
                        curr.locals.add(getVType(pool, r, pcCounter));
                    }

                    count = r.readUnsignedShort();
                    if (count > stackSize)
                        throw new IllegalStateException(owner.ownerClass() + '.' + owner.name() + ": Frame stack (" + count + ") > maximum operand stack size (" + stackSize + ")");
                    while (count > 0) {
                        count--;
                        curr.stacks.add(getVType(pool, r, pcCounter));
                    }
                }
                break;
            }

            allOffset += offset + 1;
            InsnNode n = pcCounter.get(allOffset);
            if (n != null) {
                curr.target = n;
            } else {
                System.out.println(list);
                System.out.println(curr);
                throw new IllegalArgumentException(owner.ownerClass() + '.' + owner.name() + ": Frame target null: " + allOffset);
            }

            prev = curr;
            list.add(curr);
        }
        return list;
    }

    private static Var getVType(ConstantPool pool, ByteReader r, IntBiMap<InsnNode> pcCounter) {
        int type = VarType.validate(r.readByte());
        switch (type) {
            case VarType.REFERENCE:
                String className = ((CstClass) pool.get(r)).getValue().getString();
                return new Var(className);
            case VarType.UNINITIAL:
                InsnNode node = pcCounter.get(r.readUnsignedShort());
                return new Var(node);
            default:
                return Var.std(type);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (InsnNode node : instructions) {
            sb.append("    ").append(node).append('\n');
        }
        if (!exceptions.isEmpty()) {
            sb.append("Exception Handlers: \n");
            for (ExceptionEntry ex : exceptions) {
                sb.append("    ").append(ex).append('\n');
            }
        } else if (attributes.getByName("LocalVariableTable") != null)
            sb.append("LVT: ").append(((AttrLocalVars) attributes.getByName("LocalVariableTable")).toString((AttrLocalVars) attributes.getByName("LocalVariableTypeTable")));
        if (frames != null) {
            sb.append("         StackMapTable: \n");
            for (Frame frame : frames) {
                sb.append(frame);
            }
        }
        if (attributes.getByName("Exceptions") != null) {
            sb.append("         Throws: \n");
            for (String str : ((AttrStringList) attributes.getByName("Exceptions")).classes) {
                sb.append("            ").append(str).append('\n');
            }
        }
        return sb.toString();
    }

    public void markEnd() {
        instructions.add(METHOD_END_MARK);
    }

    public AttrLocalVars getLVT() {
        return (AttrLocalVars) attributes.getByName("LocalVariableTable");
    }

    public AttrLocalVars getLVTT() {
        return (AttrLocalVars) attributes.getByName("LocalVariableTypeTable");
    }
}