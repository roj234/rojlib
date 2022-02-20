/*
 * This file is a part of MoreItems
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
package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.util.ConstantPool;
import roj.collect.IntList;
import roj.math.MutableInt;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * Code attribute visitor
 * 默认实现都是As-is的，你需要自己修改
 *
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class CodeVisitor extends Holder {
    protected int bci;

    public AttributeVisitor attributeVisitor;

    // Code长度的位置, 以及属性数量的位置
    private int codeIndex, codeAttrAmountIndex;

    private final List<Segment> segments = new ArrayList<>();
    private final List<Label>   labels   = new ArrayList<>();
    private final IntList       offsets  = new IntList();
    private int offset;

    // 属性数量
    protected int codeAttrAmount;

    public CodeVisitor() {}

    public CodeVisitor(ClassVisitor cv) {
        super(cv);
    }

    public void preVisit(ClassVisitor cv) {
        super.preVisit(cv);
        if (attributeVisitor != null)
            attributeVisitor.preVisit(cv);
    }

    public void visit(ConstantPool cp) {
        this.cp = cp;
        int len;
        ByteReader r = this.br;
        visitCode(r.readUnsignedShort(), r.readUnsignedShort(), len = r.readInt());

        visitBytecode(len);

        int len2 = r.readUnsignedShort();
        visitEndBytecodes(len2);

        for (int k = 0; k < len2; k++) {
            visitException(r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort(), (CstClass) cp.get(r));
        }

        len2 = r.readUnsignedShort();
        visitCodeAttributes(len2);
        for (int k = 0; k < len2; k++) {
            visitCodeAttribute(((CstUTF) cp.get(r)).getString(), r.readInt());
        }
        visitEndCode();
    }

    public void postVisit() {
        super.postVisit();
        if (attributeVisitor != null)
            attributeVisitor.postVisit();
    }

    public void visitCode(int stackSize, int localSize, int len) {
        bw.putShort(cw.getUtfId("Code"))
          .putInt(0);
        codeIndex = bw.wIndex();
        bw.putShort(stackSize).putShort(localSize).putInt(0);
        offset = 0;

        codeAttrAmountIndex = bw.wIndex();
        segments.clear();
    }

    protected void visitCodeNoWrap(int stackSize, int localSize) {
        codeIndex = 0;
        bw.putShort(stackSize).putShort(localSize).putInt(0);
        codeAttrAmountIndex = bw.wIndex();
        segments.clear();
    }

    public void visitBytecode(int len) {
        ByteReader r = br;
        ConstantPool pool = cp;

        int rBegin = r.rIndex;
        len += rBegin;

        int wBegin = bw.wIndex();

        int i = 0;
        byte prev = 0, code;
        while (r.rIndex < len) {
            bci = r.rIndex - rBegin;
            code = OpcodeUtil.byId(r.readByte());

            boolean widen = prev == Opcodes.WIDE;
            if (widen) {
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
                        throw new IllegalStateException("Unable to wide " + OpcodeUtil.toString0(code));
                }
            }

            switch (code) {
                case PUTFIELD:
                case GETFIELD:
                case PUTSTATIC:
                case GETSTATIC:
                    field(code, (CstRefField) pool.get(r));
                    break;

                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                    invoke(code, (CstRef) pool.get(r));
                    break;
                case INVOKEINTERFACE:
                    invoke_interface((CstRefItf) pool.get(r), r.readShort());
                    break;
                case INVOKEDYNAMIC:
                    invoke_dynamic((CstDynamic) pool.get(r), r.readUnsignedShort());
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
                    jump(code, r.readShort());
                    break;
                case GOTO_W:
                    jump_4(code, r.readInt());
                    break;

                case JSR:
                case SIPUSH:
                    std_u2(code, r.readShort());
                    break;
                case JSR_W:
                    std_u4(code, r.readInt());
                    break;
                case RET:
                    if(widen) {
                        std_u2(code, r.readShort());
                    } else {
                        std_u1(code, r.readByte());
                    }
                    break;

                case BIPUSH:
                case NEWARRAY:
                    std_u1(code, r.readByte());
                    break;

                case LDC:
                    ldc(code, pool.array(r.readUByte()));
                    break;
                case LDC_W:
                case LDC2_W:
                    ldc(code, pool.get(r));
                    break;

                case IINC:
                    increase(widen, widen ? r.readUnsignedShort() : r.readUByte(), widen ? r.readShort() : r.readByte());
                    break;

                case NEW:
                case ANEWARRAY:
                case INSTANCEOF:
                case CHECKCAST:
                    clazz(code, (CstClass) pool.get(r));
                    break;

                case MULTIANEWARRAY:
                    multi_dimension_array((CstClass) pool.get(r), r.readUByte());
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
                    if(widen) {
                        std_u2(code, r.readUnsignedShort());
                    } else {
                        std_u1(code, r.readUByte());
                    }
                    break;
                case TABLESWITCH:
                    // align
                    r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
                    parse_table_switch(r);
                    break;
                case LOOKUPSWITCH:
                    r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
                    parse_lookup_switch(r);
                    break;
                default:
                    std(code);
            }

            prev = code;
        }
    }

    // region instructions

    public void multi_dimension_array(CstClass clz, int dimension) {
        bw.put(MULTIANEWARRAY).putShort(cw.reset(clz).getIndex()).put((byte) dimension);
    }

    public void clazz(byte code, CstClass clz) {
        bw.put(code).putShort(cw.reset(clz).getIndex());
    }

    public void increase(boolean widen, int var_id, int count) {
        bw.put(IINC);
        if (widen) {
            bw.putShort(var_id).putShort(count);
        } else {
            bw.put((byte) var_id).put((byte) count);
        }
    }

    public void ldc(byte code, Constant c) {
        addSegment(new LdcSegment(code, cw.reset(c)));
    }

    public void invoke_dynamic(CstDynamic dyn, int type) {
        bw.put(INVOKEDYNAMIC).putShort(cw.reset(dyn).getIndex()).putShort(type);
    }

    public void invoke_interface(CstRefItf itf, short argc) {
        bw.put(INVOKEINTERFACE).putShort(cw.reset(itf).getIndex()).putShort(argc);
    }

    public void invoke(byte code, CstRef method) {
        bw.put(code).putShort(cw.reset(method).getIndex());
    }

    public void field(byte code, CstRefField field) {
        bw.put(code).putShort(cw.reset(field).getIndex());
    }

    public void jumpAbs(byte code, int idx) {
        jump(code, idx - bw.wIndex());
    }

    public void jump(byte code, int offset) {
        addSegment(new JumpSegment(code, relative(offset)));
    }

    public void jump_4(byte code, int offset) {
        addSegment(new JumpSegment(code, relative(offset)));
    }

    public void std(byte code) {
        bw.put(code);
    }

    public void std_u1(byte code, int value) {
        bw.put(code).put((byte) value);
    }

    public void std_u2(byte code, int value) {
        bw.put(code).putShort(value);
    }

    public void std_u4(byte code, int value) {
        bw.put(code).putInt(value);
    }

    public void parse_table_switch(ByteReader r) {
        int def = r.readInt();
        int low = r.readInt();
        int hig = r.readInt();
        int count = hig - low + 1;

        ArrayList<SwitchEntry> map = new ArrayList<>(count);

        if(count > 100000)
            throw new IllegalArgumentException("length > 100000");

        int i = 0;
        while (count > i) {
            map.add(new SwitchEntry(i++ + low, relative(r.readInt())));
        }

        addSegment(new SwitchSegment(TABLESWITCH, relative(def), map, bci));
    }

    public void parse_lookup_switch(ByteReader r) {
        int def = r.readInt();
        int count = r.readInt();

        ArrayList<SwitchEntry> map = new ArrayList<>(count);

        if(count > 100000)
            throw new IllegalArgumentException("length > 100000");

        while (count-- > 0) {
            map.add(new SwitchEntry(r.readInt(), relative(r.readInt())));
        }

        addSegment(new SwitchSegment(LOOKUPSWITCH, relative(def), map, bci));
    }

    public void add_switch_segment(byte code, int absolute_default, List<SwitchEntry> map) {
        map.sort(null);
        addSegment(new SwitchSegment(code, relative(absolute_default), map, bci));
    }

    // endregion

    private void addSegment(Segment c) {
        if (bw.isDummy()) return;

        if (segments.isEmpty()) segments.add(new StaticSegment(bw, offset = bw.wIndex() - codeAttrAmountIndex));
        else {
            StaticSegment c1 = (StaticSegment) segments.get(segments.size() - 1);
            c1.initLength();
            if (c1.length == 0) {
                segments.remove(segments.size() - 1);
            } else
            offset += c1.length;
        }

        segments.add(c);
        offset += c.length;

        StaticSegment st = new StaticSegment();
        st.startBci = offset;
        segments.add(st);
        bw = st.data;
    }

    private void satisfySegments() {
        offsets.clear();
        if (!segments.isEmpty()) {
            List<Segment> segments = this.segments;
            int begin = codeAttrAmountIndex;
            bw = ((StaticSegment) segments.get(0)).data;

            int wi = bw.wIndex();

            offsets.ensureCapacity(segments.size() << 1);
            offsets.setSize(segments.size());

            int[] summed = offsets.getRawArray();
            updateOffset(segments, summed);

            // 前一半: index-after, 后一半: index-before
            System.arraycopy(summed, 0, summed, segments.size(), segments.size());

            do {
                bci = wi - begin;
                bw.wIndex(wi);
                updateOffset(segments, summed);
                boolean delta = false;
                for (int i = 1; i < segments.size(); i++) {
                    Segment seg = segments.get(i);
                    int len = seg.length;
                    if (seg.put(this)) {
                        delta = true;
                    }

                    bci = bw.wIndex() - begin;
                }

                if (!updateOffset(segments, summed) && !delta) break;
            } while (true);
            segments.clear();
        }
        labels.clear();
    }

    private boolean updateOffset(List<Segment> segments, int[] summed) {
        summed[0] = 0;

        int i = 0;
        while (true) {
            Segment c = segments.get(i);
            c.initLength();
            if (i+1 == segments.size()) break;

            summed[i + 1] = summed[i] + c.length;

            i++;
        }

        boolean changed = false;
        for (int j = 0; j < labels.size(); j++) {
            Label label = labels.get(j);
            changed |= label.update(offsets);
        }
        return changed;
    }

    private Label relative(int rel) {
        return makeLabel(rel + bci);
    }

    protected final Label makeLabel(int pos) {
        Label l = new Label(pos);
        if (segments.isEmpty()) {
            if (pos > bci) labels.add(l);
        } else {
            if (pos > ((StaticSegment) segments.get(0)).startBci) labels.add(l);
        }
        return l;
    }

    protected final int satisfyOffset(int offset) {
        int offSize = offsets.size();
        if (offSize == 0) return offset;
        int[] sum = offsets.getRawArray();
        int code;

        findStaticBlockId:
        {
            for (int i = offSize; i < offSize<<1; i++) {
                if (sum[i] > offset) {
                    offset -= sum[code = i - 1];
                    break findStaticBlockId;
                }
            }
            offset -= sum[code = (offSize<<1) - 1];
        }
        code -= offSize;

        return offset + sum[code];
    }

    public void visitEndBytecodes(int exLen) {
        if (bw.isDummy()) return;

        satisfySegments();

        bw.putInt(codeAttrAmountIndex - 4, bw.wIndex() - codeAttrAmountIndex);

        codeAttrAmountIndex = bw.wIndex();
        codeAttrAmount = 0;
        bw.putShort(0);
    }

    public void visitException(int start, int end, int handler, CstClass type) {
        start = satisfyOffset(start);
        end = satisfyOffset(end);
        handler = satisfyOffset(handler);
        bw.putShort(start).putShort(end).putShort(handler).putShort(type == null ? 0 : cw.reset(type).getIndex());
        // 在这里是exception的数量
        codeAttrAmount++;
    }

    public void visitCodeAttributes(int l) {
        if (bw.isDummy()) return;

        bw.putShort(codeAttrAmountIndex, codeAttrAmount);

        codeAttrAmountIndex = bw.wIndex();
        codeAttrAmount = 0;
        bw.putShort(0);
    }

    public void visitCodeAttribute(String name, int length) {
        int end = br.rIndex + length;
        if (attributeVisitor != null) {
            if (attributeVisitor.visit(name, length)) {
                codeAttrAmount++;
            }
        }
        br.rIndex = end;
    }

    public void visitEndCode() {
        if (bw.isDummy()) return;

        bw.putShort(codeAttrAmountIndex, codeAttrAmount);

        if (codeIndex > 0) {
            bw.putInt(codeIndex - 4, bw.wIndex() - codeIndex);
        }
    }

    protected static final class Label extends MutableInt {
        int code, offset;

        public Label(int offset) {
            super(offset);
            this.code = -1;
            this.offset = offset;
        }

        public boolean update(IntList sum) {
            if (code < 0) {
                for (int i = 0; i < sum.size(); i++) {
                    if (sum.get(i) > offset) {
                        offset -= sum.get(code = i - 1);
                        return true;
                    }
                }
                offset -= sum.get(code = sum.size() - 1);
                return true;
            } else {
                int pos = getValue();
                int off = offset + sum.get(code);
                setValue(off);
                return pos != off;
            }
        }

        @Override
        public String toString() {
            return "[#" + code + "+" + offset + '=' + getValue() + ']';
        }
    }

    static abstract class Segment {
        int length;
        abstract boolean put(CodeVisitor to);
        void initLength() {}
    }

    static final class StaticSegment extends Segment {
        final ByteList data;
        int startBci;

        public StaticSegment() {
            this.data = new ByteList();
            length = -1;
        }

        public StaticSegment(ByteList bw, int len) {
            this.data = bw;
            this.length = len;
        }

        @Override
        void initLength() {
            if (length < 0) length = data.wIndex();
        }

        @Override
        public boolean put(CodeVisitor to) {
            to.bw.put(data);
            return false;
        }

        @Override
        public String toString() {
            return "static(" + startBci + ',' + length + ')';
        }
    }

    static final class JumpSegment extends Segment {
        byte code;
        MutableInt offset;

        public JumpSegment(byte code, MutableInt offset) {
            this.code = code;
            this.offset = offset;
            this.length = code == GOTO_W ? 5 : 3;
        }

        @Override
        @SuppressWarnings("fallthrough")
        public boolean put(CodeVisitor to) {
            ByteList bw = to.bw;
            int pl;
            int delta = offset.getValue() - to.bci;

            switch (code) {
                case GOTO:
                case GOTO_W:
                    if (((short) delta) != delta) {
                        bw.put(code = GOTO_W).putInt(delta);
                        pl = 5;
                        break;
                    } else {
                        code = GOTO;
                    }
                default:
                    pl = 3;
                    bw.put(code).putShort(delta);
            }
            return length != (length = pl);
        }

        @Override
        public String toString() {
            return "jump(" + OpcodeUtil.toString0(code) + ',' + offset + ')';
        }
    }

    static final class SwitchSegment extends Segment {
        byte code;
        MutableInt def;
        List<SwitchEntry> map;

        public SwitchSegment(int code, MutableInt def, List<SwitchEntry> map, int xp) {
            this.code = (byte) code;
            this.def = def;
            this.map = map;
            int pad = (4 - ((xp+1) & 3)) & 3;
            this.length = code == Opcodes.TABLESWITCH ?
                    1 + pad + 4 + 8 + (map.size() << 2) :
                    1 + pad + 8 + (map.size() << 3);
        }

        @Override
        public boolean put(CodeVisitor to) {
            ByteList w = to.bw;
            int op = w.wIndex();

            int self = to.bci;

            w.put(code);

            int p = w.wIndex();
            int d = (4 - ((p - to.codeAttrAmountIndex) & 3)) & 3;
            w.wIndex(p + d);

            byte[] list = w.list;
            while (d-- > 0) list[p++] = 0;

            if (this.code == TABLESWITCH) {
                int lo = map.get(0).key;
                int hi = map.get(map.size() - 1).key;

                w.putInt(def.getValue() - self)
                 .putInt(lo).putInt(hi);
                for (SwitchEntry node : map) {
                    w.putInt(node.getBci() - self);
                }
            } else {
                w.putInt(def.getValue() - self)
                 .putInt(map.size());
                for (SwitchEntry entry : map) {
                    w.putInt(entry.key)
                     .putInt(entry.getBci() - self);
                }
            }

            return length != (length = w.wIndex() - op);
        }

        @Override
        public String toString() {
            return "switch(" + OpcodeUtil.toString0(code) + ',' + length + ')';
        }
    }

    static final class LdcSegment extends Segment {
        byte code;
        Constant c;

        public LdcSegment(byte code, Constant c) {
            this.code = code;
            this.c = c;
            length = code == LDC ? 2 : 3;
        }

        @Override
        @SuppressWarnings("fallthrough")
        boolean put(CodeVisitor to) {
            ByteList bw = to.bw;
            int pl;
            int cpi = c.getIndex();

            switch (code) {
                case LDC:
                case LDC_W:
                    if (cpi < 256) {
                        bw.put(code = LDC).put((byte) cpi);
                        pl = 2;
                        break;
                    } else {
                        code = LDC_W;
                    }
                default:
                    pl = 3;
                    bw.put(code).putShort(cpi);
            }
            return length != (length = pl);
        }

        @Override
        public String toString() {
            return "ldc(" + c + ')';
        }
    }
}
