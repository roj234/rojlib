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

import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;
import roj.util.ByteReader;

import static roj.asm.Opcodes.*;

/**
 * Code attribute visitor
 * 默认实现都是As-is的，你需要自己修改
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/16 19:07
 */
public class CodeVisitor extends Holder {
    protected int bci;

    public AttributeVisitor attributeVisitor;

    // Code长度的位置, 以及属性数量的位置
    private int codeIndex, codeAttrAmountIndex;

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
        bw.writeShort(cw.getUtfId("Code"));
        bw.writeInt(0);
        codeIndex = bw.list.pos();
        bw.writeShort(stackSize).writeShort(localSize).writeInt(0);

        codeAttrAmountIndex = bw.list.pos();
    }

    protected void visitCodeNoWrap(int stackSize, int localSize) {
        codeIndex = 0;
        bw.writeShort(stackSize).writeShort(localSize).writeInt(0);
        codeAttrAmountIndex = bw.list.pos();
    }

    public void visitBytecode(int len) {
        ByteReader r = this.br;
        ConstantPool pool = this.cp;

        int begin = r.index;
        len += begin;

        byte prev = 0, code;
        while (r.index < len) {
            bci = r.index - begin;
            code = Opcodes.byId(r.readByte());

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
                        throw new IllegalStateException("Unable to wide " + Opcodes.toString0(code));
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
                    r.index += (4 - ((r.index - begin) & 3)) & 3;
                    parse_table_switch(r);
                    break;
                case LOOKUPSWITCH:
                    r.index += (4 - ((r.index - begin) & 3)) & 3;
                    parse_lookup_switch(r);
                    break;
                default:
                    std(code);
            }

            prev = code;
        }
    }

    public void multi_dimension_array(CstClass clz, int dimension) {
        bw.writeByte(MULTIANEWARRAY).writeShort(cw.reset(clz).getIndex()).writeByte((byte) dimension);
    }

    public void clazz(byte code, CstClass clz) {
        bw.writeByte(code).writeShort(cw.reset(clz).getIndex());
    }

    public void increase(boolean widen, int var_id, int count) {
        bw.writeByte(IINC);
        if (widen) {
            bw.writeShort(var_id).writeShort(count);
        } else {
            bw.writeByte((byte) var_id).writeByte((byte) count);
        }
    }

    public void ldc(byte code, Constant c) {
        int cpi = cw.reset(c).getIndex();
        if (code == Opcodes.LDC2_W || (code = (cpi < 256) ? Opcodes.LDC : Opcodes.LDC_W) != Opcodes.LDC) {
            bw.writeByte(code).writeShort(cpi);
        } else {
            bw.writeByte(code).writeByte((byte) cpi);
        }
    }

    public void invoke_dynamic(CstDynamic dyn, int type) {
        bw.writeByte(INVOKEDYNAMIC).writeShort(cw.reset(dyn).getIndex()).writeShort(type);
    }

    public void invoke_interface(CstRefItf itf, short argc) {
        bw.writeByte(INVOKEINTERFACE).writeShort(cw.reset(itf).getIndex()).writeShort(argc);
    }

    public void invoke(byte code, CstRef method) {
        bw.writeByte(code).writeShort(cw.reset(method).getIndex());
    }

    public void field(byte code, CstRefField field) {
        bw.writeByte(code).writeShort(cw.reset(field).getIndex());
    }

    // 关于jump的offset问题还有待处理
    public void jump(byte code, int offset) {
        std_u2(code, offset);
    }

    public void jump_4(byte code, int offset) {
        std_u4(code, offset);
    }

    public void std(byte code) {
        bw.writeByte(code);
    }

    public void std_u1(byte code, int value) {
        bw.writeByte(code).writeByte((byte) value);
    }

    public void std_u2(byte code, int value) {
        bw.writeByte(code).writeShort(value);
    }

    public void std_u4(byte code, int value) {
        bw.writeByte(code).writeInt(value);
    }

    public void parse_table_switch(ByteReader r) {
        align_out();

        int def = r.readInt();
        int low = r.readInt();
        int hig = r.readInt();
        int count = hig - low + 1;

        bw.writeInt(def).writeInt(low).writeInt(hig);

        if(count > 100000)
            throw new IllegalArgumentException("length > 100000");

        int i = 0;
        while (count > i) {
            int key = i++ + low;
            int offset = r.readInt();

            bw.writeInt(offset);
        }
    }

    public void align_out() {
        ByteList out = bw.list;
        out.pos(out.pos() + (3 - ((out.pos() - codeAttrAmountIndex - 4) & 3)));
    }

    public void parse_lookup_switch(ByteReader r) {
        align_out();

        int def = r.readInt();
        int count = r.readInt();

        bw.writeInt(def).writeInt(count);

        if(count > 100000)
            throw new IllegalArgumentException("length > 100000");


        while (count > 0) {
            int key = r.readInt();
            int offset = r.readInt();
            count--;

            bw.writeInt(key).writeInt(offset);
        }
    }

    public void visitEndBytecodes(int exLen) {
        int pos = bw.list.pos();
        bw.list.pos(codeAttrAmountIndex - 4);
        bw.writeInt(pos - codeAttrAmountIndex);
        bw.list.pos(pos);

        codeAttrAmountIndex = bw.list.pos();
        codeAttrAmount = 0;
        bw.writeShort(0);
    }

    public void visitException(int start, int end, int handler, CstClass type) {
        bw.writeShort(start).writeShort(end).writeShort(handler).writeShort(type == null ? 0 : cw.reset(type).getIndex());
        // 在这里是exception的数量
        codeAttrAmount++;
    }

    public void visitCodeAttributes(int l) {
        int pos = bw.list.pos();
        bw.list.pos(codeAttrAmountIndex);
        bw.writeShort(codeAttrAmount);
        bw.list.pos(pos);

        codeAttrAmountIndex = pos;
        codeAttrAmount = 0;
        bw.writeShort(0);
    }

    public void visitCodeAttribute(String name, int length) {
        int end = br.index + length;
        if (attributeVisitor != null) {
            if (attributeVisitor.visit(name, length)) {
                codeAttrAmount++;
            }
        }
        br.index = end;
    }

    public void visitEndCode() {
        int pos = bw.list.pos();
        bw.list.pos(codeAttrAmountIndex);
        bw.writeShort(codeAttrAmount);

        if (codeIndex > 0) {
            bw.list.pos(codeIndex - 4);
            bw.writeInt(pos - codeIndex);
        }
        bw.list.pos(pos);
    }
}
