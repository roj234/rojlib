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

package roj.asm.tree.attr;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.frame.*;
import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.*;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.asm.util.ExceptionEntry;
import roj.asm.util.InsnList;
import roj.collect.*;
import roj.util.ByteList;
import roj.util.ByteList.WriteOnly;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * 默认情况, 在InsnList最后总会有一个 {@link EndOfInsn#MARKER}
 *
 * @author Roj234
 * @version 1.3
 * @since 2021/6/18 9:51
 */
public class AttrCode extends Attribute {
    public static final byte COMPUTE_FRAMES = 1, COMPUTE_SIZES = 2;
    @Nonnull
    private final MethodNode owner;

    public final InsnList instructions = new InsnList();

    public char stackSize, localSize;

    public byte interpretFlags;
    public List<Frame> frames;
    public final ArrayList<ExceptionEntry> exceptions = new ArrayList<>();

    public final AttributeList attributes = new AttributeList();

    public AttrCode(MethodNode method) {
        super("Code");
        this.owner = method;
        // noinspection all
        method.getClass();
    }

    public AttrCode(MethodNode method, ByteList list, ConstantPool pool) {
        this(method, new ByteReader(list), pool);
    }

    public AttrCode(MethodNode owner, ByteReader r, ConstantPool cp) {
        this(owner);
        this.stackSize = r.readChar();
        this.localSize = r.readChar();

        int largestIndex = r.readInt();
        if (largestIndex < 1 || largestIndex > r.remaining())
            throw new IllegalStateException("Wrong code attribute: " + largestIndex + " of length at " + owner.ownerClass() + "." + owner.name() + " remaining " + r.remaining());
        IntMap<InsnNode> pc = parseCode(cp, r, largestIndex);

        int len = r.readUnsignedShort();
        exceptions.ensureCapacity(len);
        // start_pc,end_pc,handler_pc中的值都表示的是PC计数器中的指令地址
        //exception_table表示的意思是：
        //如果字节码从第start_pc行到第end_pc行之间出现了catch_type所描述的异常类型，那么将跳转到handler_pc行继续处理。
        // catchType => ConstantClass extends Throwable
        for (int i = 0; i < len; i++) {
            int pci;
            this.exceptions.add(new ExceptionEntry(
                    pc.get(r.readUnsignedShort()), // start
                    (pci = r.readUnsignedShort()) == largestIndex ? EndOfInsn.MARKER : pc.get(pci), // end
                    pc.get(r.readUnsignedShort()), // handler
                    (CstClass) cp.get(r)));      // type
        }
        instructions.add(EndOfInsn.MARKER);

        len = r.readUnsignedShort();
        // attributes

        try {
            attributes.ensureCapacity(len);
            for (int i = 0; i < len; i++) {
                String name = ((CstUTF) cp.get(r)).getString();
                int end = r.readInt() + r.rIndex;
                switch (name) {
                    case "LineNumberTable":
                        attributes.add(new AttrLineNumber(r, pc));
                        break;
                    case "LocalVariableTable":
                    case "LocalVariableTypeTable":
                        // LocalVariableTypeTable是可选的
                        attributes.add(new AttrLocalVars(name, cp, r, pc, largestIndex));
                    break;
                    case "StackMapTable":
                        this.frames = new ArrayList<>();
                        readStackMap(cp, r, pc);
                    break;
                    case "Exceptions":
                        attributes.add(new AttrStringList(name, r, cp, 1));
                        break;
                    case "RuntimeInvisibleTypeAnnotations":
                    case "RuntimeVisibleTypeAnnotations":
                        attributes.add(new AttrTypeAnnotation(name, r, cp, this));
                        break;
                    default:
                        System.err.println("[R.A.AC]Skip unknown " + name + " for " + (owner.ownerClass() + '.' + owner.name()));
                }
                if (r.rIndex != end) {
                    System.err.println("[R.A.AC]" + (end - r.rIndex) + " bytes not read correctly!");
                    r.rIndex = end;
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(owner.ownerClass() + '.' + owner.name() + ": read attribute", e);
        }
    }

    @Override
    protected void toByteArray1(ConstantPool cw, ByteList w) {
        ToIntMap<InsnNode> pcRev = reIndex(instructions, cw, new ToIntMap<InsnNode>(instructions.size()) {
            @Override
            public int getInt(InsnNode key) {
                int v = super.getOrDefault(key, -1);
                if(v == -1) {
                    System.out.println("Self " + this);
                    System.out.println("Insn " + instructions);
                    throw new IllegalArgumentException("Couldn't find bci for " + key);
                }
                return v;
            }
        });
        if (interpretFlags != 0) recalculateFrames(pcRev);

        w.putShort(this.stackSize).putShort(this.localSize).putInt(0);

        int lenIdx = w.wIndex();

        InsnList insn = this.instructions;
        for (int i = 0; i < insn.size(); i++) {
            insn.get(i).toByteArray(cw, w);
        }

        int cp = w.wIndex();
        w.wIndex(lenIdx - 4);
        w.putInt(cp - lenIdx).wIndex(cp);

        w.putShort(this.exceptions.size());
        ArrayList<ExceptionEntry> exs = this.exceptions;

        for (int i = 0; i < exs.size(); i++) {
            ExceptionEntry ex = exs.get(i);
            ex.start  = InsnNode.validate(ex.start);
            ex.end    = InsnNode.validate(ex.end);
            ex.handler= InsnNode.validate(ex.handler);
            w.putShort(pcRev.getInt(ex.start))
             .putShort(pcRev.getInt(ex.end))
             .putShort(pcRev.getInt(ex.handler))
             .putShort(ex.type == ExceptionEntry.ANY_TYPE ? 0 : cw.getClassId(ex.type));
        }

        final AttributeList attrs = this.attributes;
        w.putShort(attrs.size() + (frames == null ?0 : 1));
        for (int i = 0; i < attrs.size(); i++) {
            Attribute attribute = attrs.get(i);
            if(attribute instanceof ICodeAttribute) {
                w.putShort(cw.getUtfId(attribute.name)).putInt(0);

                lenIdx = w.wIndex();
                ((ICodeAttribute) attribute).toByteArray(cw, w, pcRev);
                cp = w.wIndex();
                w.wIndex(lenIdx - 4);
                w.putInt(cp - lenIdx).wIndex(cp);
            } else {
                attribute.toByteArray(cw, w);
            }
        }

        if (this.frames != null) {
            w.putShort(cw.getUtfId("StackMapTable")).putInt(0);

            lenIdx = w.wIndex();
            writeFrames(cw, w.putShort(frames.size()), pcRev);

            cp = w.wIndex();
            w.wIndex(lenIdx - 4);
            w.putInt(cp - lenIdx).wIndex(cp);
        }

        insn.add(EndOfInsn.MARKER);
    }

    /**
     * 重新计算node的index并预先获得常量池索引
     *
     * @param cw 常量池
     */
    public static <T extends IIntMap<InsnNode>> T reIndex(InsnList insn, ConstantPool cw, T pcRev) {
        if (insn.isEmpty()) {
            return pcRev;
        }
        InsnNode last = insn.get(insn.size() - 1);
        if (last == EndOfInsn.MARKER) {
            insn.remove(insn.size() - 1);
        }

        int pos = 0;
        ByteList w = new WriteOnly();

        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            if (node.nodeType() == InsnNode.T_LDC) {
                node.toByteArray(cw, w);
            }
        }
        w.wIndex(0);

        int j = 3;
        //boolean violation = false;
        o:
        do {
            boolean lciRf = false;
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                pcRev.putInt(node, pos);
                // noinspection all
                //if (null != pcRev.putInt(node, pos) && j == 3)
                //    violation = true;

                switch (node.nodeType()) {
                    case InsnNode.T_LDC:
                        int t = node.nodeSize();
                        node.toByteArray(cw, w);
                        if (w.wIndex() != t) {
                            lciRf = true;
                        }
                        w.wIndex(0);
                        break;
                    case InsnNode.T_SWITCH:
                        ((SwitchInsnNode) node).pad(pos, pcRev);
                        break;
                }
                int t = node.nodeSize();
                if (t <= 0)
                    throw new IllegalArgumentException("Node " + node + " (A " + node.getClass().getName() + ") is not writable");
                pos += t;
            }
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                if (node.nodeType() == InsnNode.T_GOTO_IF) {
                    //if (violation) throw new IllegalArgumentException("带跳转的代码中不应出现重复对象");
                    GotoInsnNode gin = (GotoInsnNode) node;
                    if (gin.review(pcRev) || lciRf) {
                        pos = 0;
                        // idea又出错了
                        // noinspection all
                        if (j-- == 0)
                            throw new IllegalStateException("Unable to satisfy goto(_w) calls");
                        continue o;
                    }
                }
            }
            break;
        } while (true);

        if (j != 3) {
            System.err.println("Recursion " + (3 - j));
        }
        pcRev.putInt(EndOfInsn.MARKER, pos);

        return pcRev;
    }

    // region readCode

    public IntMap<InsnNode> parseCode(ConstantPool pool, ByteReader r, int len) {
        IntMap<InsnNode> pci = new IntMap<InsnNode>(Math.min(len / 2, 100000)) {
            @Override
            public InsnNode get(int id) {
                InsnNode node = super.get(id);
                if (node == null)
                    throw new NullPointerException("Node is null: " + owner.ownerClass() + "." + owner.name() + " (bci:" + id + ")\n" + entrySet());
                return node;
            }
        };
        int begin = r.rIndex;
        len += begin;

        int negXXX = 0;
        InsnList insn = this.instructions;
        int idxBegin = 0;
        InsnNode curr = null;
        boolean widen = false;
        while (r.rIndex < len) {
            try {
                byte code = OpcodeUtil.byId(r.readByte());
                if (widen) Interpreter.checkWide(code);
                switch (code) {
                    case PUTFIELD:
                    case GETFIELD:
                    case PUTSTATIC:
                    case GETSTATIC:
                        curr = new FieldInsnNode(code, (CstRefField) pool.get(r));
                        break;
                    case INVOKEVIRTUAL:
                    case INVOKESPECIAL:
                    case INVOKESTATIC:
                        curr = new InvokeInsnNode(code, (CstRef) pool.get(r));
                        break;
                    case INVOKEINTERFACE:
                        curr = new InvokeItfInsnNode((CstRefItf) pool.get(r), r.readShort());
                        break;
                    case INVOKEDYNAMIC:
                        curr = new InvokeDynInsnNode((CstDynamic) pool.get(r), r.readUnsignedShort());
                        break;
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
                        curr = new JmPrimer(code, r.readShort());
                        break;
                    case GOTO_W:
                        curr = new JmPrimer(code, r.readInt());
                        break;

                    case JSR:
            /*  There are some parts of the JVM specification which suggest that
                    the operations JSR (Jump SubRoutine), JSR_W (Jump SubRoutine Wide) and RET (RETurn from subroutine) may be
                    used only up to class file version 50.0 (JDK 1.6):
            */
                        throwUnsupportedCode();
                        curr = new U2InsnNode(code, r.readShort());
                        break;
                    case JSR_W:
                        throwUnsupportedCode();
                        curr = new U4InsnNode(code, r.readInt());
                        break;
                    case RET:
                        throwUnsupportedCode();
                        curr = widen ? new U2InsnNode(code, r.readShort()) : new U1InsnNode(code, r.readByte());
                        break;
                    case SIPUSH:
                        curr = new U2InsnNode(code, r.readShort());
                        break;
                    case BIPUSH:
                    case NEWARRAY:
                        curr = new U1InsnNode(code, r.readByte());
                        break;
                    case LDC:
                        curr = new LdcInsnNode(code, pool.array(r.readUByte()));
                        break;
                    case LDC_W:
                    case LDC2_W:
                        curr = new LdcInsnNode(code, pool.get(r));
                        break;
                    case IINC:
                        curr = widen ? new IncrInsnNode(r.readUnsignedShort(), r.readShort()) : new IncrInsnNode(r.readUByte(), r.readByte());
                        break;
                    case NEW:
                    case ANEWARRAY:
                    case INSTANCEOF:
                    case CHECKCAST:
                        curr = new ClassInsnNode(code, (CstClass) pool.get(r));
                        break;
                    case MULTIANEWARRAY:
                        curr = new MDArrayInsnNode((CstClass) pool.get(r), r.readUByte());
                        break;
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
                        curr = widen ? new U2InsnNode(code, r.readUnsignedShort()) : new U1InsnNode(code, r.readUByte());
                        break;
                    case TABLESWITCH:
                        // align
                        r.rIndex += (4 - ((r.rIndex - begin) & 3)) & 3;
                        curr = parseTableSwitch(r);
                        break;
                    case LOOKUPSWITCH:
                        r.rIndex += (4 - ((r.rIndex - begin) & 3)) & 3;
                        curr = parseLookupSwitch(r);
                        break;
                    default:
                        curr = new NPInsnNode(code);
                        break;
                }
                insn.add(curr);
            } catch (Throwable e) {
                final PrintStream err = System.err;

                err.println("Please report to Roj234 with all messages below.");
                err.println("====================PARSING NODE====================");
                err.println("Prev: " + curr);
                r.rIndex = begin + idxBegin;
                try {
                    err.println("Except: " + OpcodeUtil.toString0(r.readByte()));
                } catch (Throwable e111) {
                    r.rIndex = begin + idxBegin;
                    err.println("Except: " + r.readByte());
                }
                err.println();
                err.println("PrevIndex: " + idxBegin);
                err.println("Index: " + (r.rIndex - begin));
                err.println(r.bytes().slice(begin, len - begin));
                err.println("====================PARSING NODE====================");

                throw new InternalError("At " + (owner.ownerClass() + '.' + owner.name()), e);
            }
            if (curr.nodeType() == 123) {
                // noinspection all
                JmPrimer jp = (JmPrimer) curr;
                // 预处理
                if (jp.switcher == null && jp.def < 0) {
                    insn.set(insn.size() - 1, curr = jp.bake(validateJump(pci, idxBegin + jp.def, jp)));
                } else {
                    jp.selfIndex = idxBegin;
                    jp.arrayIndex = insn.size() - 1;
                    pci.put(--negXXX, jp);
                }
            }

            widen = curr.code == Opcodes.WIDE;
            pci.put(idxBegin, curr);
            idxBegin = r.rIndex - begin;
        }
        // 为什么初始化容量是 len / 2 :
        /*float v = (float) MathUtils.getMin2PowerOf(pci.size()) / MathUtils.getMin2PowerOf(len - begin);
        if (avg == 0) {
            avg = v;
        } else {
            avg = (amount++ * avg + v) / amount;
            if ((amount & 16383) == 0) System.out.println("avg " + avg);
        }*/

        while (negXXX < 0) {
            JmPrimer n = (JmPrimer) pci.remove(negXXX++);

            InsnNode target = validateJump(pci, n.selfIndex + n.def, n);
            if (n.switcher == null) {
                insn.set(n.arrayIndex, target = n.bake(target));
            } else {
                LinkedIntMap<Object> map = Helpers.cast(n.switcher);
                for (IntMap.Entry<Object> entry : map.entrySet()) {
                    entry.setValue(validateJump(pci, n.selfIndex + (int) entry.getValue(), n));
                }

                insn.set(n.arrayIndex, target = new SwitchInsnNode(n.code, target, Helpers.cast(map)));
            }
            pci.put(n.selfIndex, target);
        }

        return pci;
    }

    private InsnNode validateJump(IntMap<InsnNode> pci, int index, JmPrimer self) {
        InsnNode node = pci.getOrDefault(index - 1, null);
        if(node != null && node.getOpcode() == WIDE)
            throw new IllegalArgumentException("Jump target must not \"after\" wide");
        return pci.get(index);
    }

    private static void throwUnsupportedCode() {
        final PrintStream err = System.err;
        err.println("=====================================================");
        err.println("|      JSR or RET was found in your class file      |");
        err.println("|   It's deprecated and only be used for a little.  |");
        err.println("|     So there's NO SOLUTION to rewrite them.       |");
        err.println("=====================================================");
    }

    private static JmPrimer parseTableSwitch(ByteReader r) {
        int def = r.readInt();
        int low = r.readInt();
        int hig = r.readInt();
        int count = hig - low + 1;

        if(count <= 0 || count > 100000)
            throw new IllegalArgumentException("length err " + count);

        LinkedIntMap<Integer> mapping = new LinkedIntMap<>(count);
        int i = 0;
        while (count > i) {
            mapping.put(i++ + low, r.readInt());
        }

        return new JmPrimer(Opcodes.TABLESWITCH, def, mapping);
    }

    private static JmPrimer parseLookupSwitch(ByteReader r) {
        int def = r.readInt();
        int count = r.readInt();

        // 草泥马，真的有这种人中人么
        //switch (def) {
        //    default:
        //        // do sth
        //}
        // 有的，
        //====================PARSING NODE====================
        //Prev: 0x1b 加载第1个int
        //Except: 0xab 二分switch
        //
        //PrevIndex: 1
        //Index: 2
        //ByteList{
        //             0 1  2 3  4 5  6 7  8 9  a b  c d  e f
        //0x00000000   1BAB 0000 0000 000B 0000 0000 BB00 7E59
        //0x00000020   1B1C B700 814E 2DB0 }
        //====================PARSING NODE====================
        // java.lang.InternalError: At com/sk89q/worldedit/schematic/SchematicFormat.getBlockForId
        //Caused by: java.lang.IllegalArgumentException: length err 0

        if(count < 0 || count > 100000)
            throw new IllegalArgumentException("length err " + count);

        LinkedIntMap<Integer> mapping = new LinkedIntMap<>(count);
        while (count > 0) {
            mapping.put(r.readInt(), r.readInt());
            count--;
        }
        return new JmPrimer(Opcodes.LOOKUPSWITCH, def, mapping);
    }

    // endregion
    // region StackFrameTable

    @SuppressWarnings("fallthrough")
    private void writeFrames(ConstantPool pool, ByteList w, ToIntMap<InsnNode> pcRev) {
        Frame prev = getFirstFrame();
        for (int j = 0; j < frames.size(); j++) {
            Frame curr = frames.get(j);

            int offset = pcRev.getInt(InsnNode.validate(curr.target));
            if (prev.target != null) offset -= pcRev.getInt(InsnNode.validate(prev.target)) + 1;
            if (offset > 65535 || offset < 0) {
                if (prev.target != null)
                    System.err.println("Prev bci: " + pcRev.getInt(InsnNode.validate(prev.target)));
                System.err.println("Curr bci: " + pcRev.getInt(InsnNode.validate(curr.target)));

                System.err.println("Prev curr: " + prev);
                System.err.println("Curr curr: " + curr);

                throw new IllegalArgumentException("Illegal curr offset");
            }

            char type = curr.type;
            switch (curr.type) {
                case FrameType.same:
                    if (offset < 64) {
                        type = (char) offset;
                    } else {
                        curr.type = type = FrameType.same_ex;
                    }
                    break;
                case FrameType.same_local_1_stack:
                    if (offset < 64) {
                        type = (char) (offset + 64);
                    } else {
                        curr.type = type = FrameType.same_local_1_stack_ex;
                    }
                    break;
                case FrameType.chop:
                    type = (char) (251 - (prev.locals.size - curr.locals.size));
                    break;
                case FrameType.append:
                    type = (char) (251 + (curr.locals.size - prev.locals.size));
                    break;
                case FrameType.same_ex:
                case FrameType.same_local_1_stack_ex:
                case FrameType.full:
                    break;
            }
            w.put((byte) type);
            switch (curr.type) {
                case FrameType.same:
                    break;
                case FrameType.same_local_1_stack_ex:
                    w.putShort(offset);
                case FrameType.same_local_1_stack:
                    putVar(curr.stacks.get(0), w, pool, pcRev);
                    break;
                case FrameType.chop:
                    // 251 - type
                case FrameType.same_ex:
                    w.putShort(offset);
                    break;
                case FrameType.append:
                    w.putShort(offset);
                    //writeVerify(curr.stacks.get(0), w, pool);
                    for (int i = curr.locals.size + 251 - type, e = curr.locals.size; i < e; i++) {
                        putVar(curr.locals.get(i), w, pool, pcRev);
                    }
                    break;
                case FrameType.full:
                    w.putShort(offset).putShort(curr.locals.size);
                    for (int i = 0, e = curr.locals.size; i < e; i++) {
                        putVar(curr.locals.get(i), w, pool, pcRev);
                    }

                    w.putShort(curr.stacks.size);
                    for (int i = 0, e = curr.stacks.size; i < e; i++) {
                        putVar(curr.stacks.get(i), w, pool, pcRev);
                    }
            }

            prev = curr;
        }
    }

    private static void putVar(Var v, ByteList w, ConstantPool pool, ToIntMap<InsnNode> pcRev) {
        w.put(v.type);
        switch (v.type) {
            case VarType.REFERENCE:
                w.putShort(pool.getClassId(v.owner));
                break;
            case VarType.UNINITIAL:
                w.putShort(pcRev.getInt(InsnNode.validate(v.node)));
                break;
        }
    }

    private void readStackMap(ConstantPool pool, ByteReader r, IntMap<InsnNode> pc) {
        List<Frame> list = this.frames;
        list.clear();

        Frame prev = getFirstFrame();

        int allOffset = -1;
        int tableLen = r.readUnsignedShort();
        for (int k = 0; k < tableLen; k++) {
            int type = r.readUByte();
            Frame curr = new Frame(FrameType.byId(type));

            curr.locals.copyFrom(prev.locals); // copy

            int offset = -1;
            switch (curr.type) {
                case FrameType.same:
                    offset = type;
                    // 变量 相同
                    // 栈 空
                    break;
                case FrameType.same_ex:
                    offset = r.readUnsignedShort();
                    // 同上
                    break;
                case FrameType.same_local_1_stack:
                    offset = type - 64;
                    curr.stacks.removeTo(1);
                    curr.stacks.set(0, getVar(pool, r, pc));
                    // 变量 相同
                    // 栈 一个
                    break;
                case FrameType.same_local_1_stack_ex:
                    offset = r.readUnsignedShort();
                    curr.stacks.removeTo(1);
                    curr.stacks.set(0, getVar(pool, r, pc));
                    // 同上
                    break;
                case FrameType.chop:
                    offset = r.readUnsignedShort();
                    if (curr.locals.size < 251 - type) {
                        throw new IllegalStateException(owner.ownerClass() + '.' + owner.name() + ": Chop(" + (251 - type) + "): too few: " + curr.locals);
                    }
                    curr.locals.pop(251 - type);
                    // 变量 k个没了
                    // 栈 空
                    break;
                case FrameType.append: {
                    offset = r.readUnsignedShort();
                    int count = type - 251;
                    while (count > 0) {
                        count--;
                        curr.locals.add(getVar(pool, r, pc));
                    }
                    if (count < 0) {
                        System.out.println(curr);
                        throw new IllegalStateException(String.valueOf(count));
                    }
                    // 变量 增加 k 个
                    // 栈 空
                }
                break;
                case FrameType.full: {
                    offset = r.readUnsignedShort();

                    curr.locals.clear();
                    curr.stacks.clear();

                    int count = r.readUnsignedShort();
                    if (count > localSize)
                        throw new IllegalStateException(owner.ownerClass() + '.' + owner.name() + ": Frame local (" + count + ") > maximum local variable size (" + localSize + ")");

                    curr.locals.ensureCapacity(count);
                    while (count > 0) {
                        count--;
                        curr.locals.add(getVar(pool, r, pc));
                    }

                    count = r.readUnsignedShort();
                    if (count > stackSize)
                        throw new IllegalStateException(owner.ownerClass() + '.' + owner.name() + ": Frame stack (" + count + ") > maximum operand stack size (" + stackSize + ")");

                    curr.locals.ensureCapacity(count);
                    while (count > 0) {
                        count--;
                        curr.stacks.add(getVar(pool, r, pc));
                    }
                }
                break;
            }

            allOffset += offset + 1;
            InsnNode n = pc.get(allOffset);
            if (n != null) {
                curr.target = n;
            } else {
                System.out.println(list);
                System.out.println(curr);
                throw new IllegalArgumentException(owner.ownerClass() + '.' + owner.name() + ": Frame target null: " + allOffset);
            }

            list.add(prev = curr);
        }
    }

    private static Var getVar(ConstantPool pool, ByteReader r, IntMap<InsnNode> pcCounter) {
        int type = VarType.validate(r.readByte());
        switch (type) {
            case VarType.REFERENCE:
                String className = ((CstClass) pool.get(r)).getValue().getString();
                return new Var(className);
            case VarType.UNINITIAL:
                InsnNode node = pcCounter.get(r.readUnsignedShort());
                return new Var(node);
            case VarType.UNINITIAL_THIS:
                return Var.READ_ONLY_UNI_THIS;
            default:
                return Var.std(type);
        }
    }

    private Frame firstFrame;
    private Frame getFirstFrame() {
        if (firstFrame == null) {
            Interpreter ft = new Interpreter();
            ft.init(owner);

            InsnList insn = instructions;
            for (int i = 0; i < insn.size(); ) {
                switch (ft.visitNode(insn.get(i++))) {
                    case 1: // return
                    case 2: // goto
                    case 3: // if
                        return firstFrame = ft.build(null);
                }
            }
        }
        return firstFrame;
    }

    /**
     * 重新计算栈帧
     */
    private void recalculateFrames(ToIntMap<InsnNode> pcRev) {
        Interpreter ft = new Interpreter();
        ft.init(owner);

        List<Frame> compute;
        try {
            compute = ft.compute(instructions, exceptions, pcRev);
        } catch (Throwable e) {
            throw new RuntimeException("At: " + owner.ownerClass() + '.' + owner.name() + ": " + this, e);
        }
        if ((interpretFlags & COMPUTE_FRAMES) != 0) {
            if (compute.isEmpty()) {
                if (frames != null)
                    frames.clear();
                frames = null;
            } else {
                if (frames == null) frames = new ArrayList<>();
                else frames.clear();
                frames.addAll(compute);
            }
        }
        if ((interpretFlags & COMPUTE_SIZES) != 0) {
            stackSize = (char) ft.maxStackSize;
            localSize = (char) ft.maxLocalSize;
        }

        // later, maybe less than a year, will change to CodeBlock methods
        ft.init(owner);

        InsnList insn = this.instructions;
        o:
        for (int i = 0; i < insn.size();) {
            switch (ft.visitNode(insn.get(i++))) {
                case 1: // return
                case 2: // goto
                case 3: // if
                    firstFrame = ft.build(null);
                    break o;
            }
        }
    }

    // endregion

    public String toString() {
        StringBuilder sb = new StringBuilder();
        IntBiMap<InsnNode> pci = instructions.getPCMap();
        for (int i = 0; i < instructions.size(); i++) {
            InsnNode n = instructions.get(i);
            sb.append("    ").append(pci.getInt(n)).append(' ').append(n).append('\n');
        }
        if (!exceptions.isEmpty()) {
            sb.append("    Exception Handlers: \n");
            for (ExceptionEntry ex : exceptions) {
                sb.append("        ").append(ex).append('\n');
            }
        } else if (attributes.getByName("LocalVariableTable") != null)
            sb.append("    LVT: ").append(((AttrLocalVars) attributes.getByName("LocalVariableTable")).toString((AttrLocalVars) attributes.getByName("LocalVariableTypeTable")));
        if (frames != null) {
            sb.append("    StackMapTable: \n");
            for (int i = 0; i < frames.size(); i++) {
                sb.append(frames.get(i));
            }
        }
        if (attributes.getByName("Exceptions") != null) {
            sb.append("    Throws: \n");
            List<String> classes = ((AttrStringList) attributes.getByName("Exceptions")).classes;
            for (int i = 0; i < classes.size(); i++) {
                sb.append("        ").append(classes.get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    public AttrLocalVars getLVT() {
        return (AttrLocalVars) attributes.getByName("LocalVariableTable");
    }

    public AttrLocalVars getLVTT() {
        return (AttrLocalVars) attributes.getByName("LocalVariableTypeTable");
    }

    public void clear() {
        exceptions.clear();
        instructions.clear();
        frames = null;
        attributes.clear();
        interpretFlags = COMPUTE_FRAMES | COMPUTE_SIZES;
    }
}