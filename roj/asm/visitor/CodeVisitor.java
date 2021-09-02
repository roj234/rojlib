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
import roj.asm.util.ConstantWriter;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/16 19:07
 */
public abstract class CodeVisitor {
    public ByteWriter     bw;
    public ConstantWriter cw;
    public ByteReader     br;
    public ConstantPool   cp;

    public ByteList code;
    protected int bci;

    // 方法的Code属性的长度的位置
    protected int codeIndex;

    // 方法的Code的属性 还有 字节码的长度
    protected int codeAttrAmountIndex, codeAttrAmount;

    public void preVisit(ByteWriter bw, ConstantWriter cw, ByteReader br, ConstantPool cp) {
        this.bw = bw;
        this.cw = cw;
        this.br = br;
        this.cp = cp;
    }

    public void visit() {
        int len;
        visitCode(br.readUnsignedShort(), br.readUnsignedShort(), len = br.readInt());

        code = br.readBytesDelegated(len);
        visitBytecode(cp);

        int len2 = br.readUnsignedShort();
        visitEndBytecodes(len2);

        for (int k = 0; k < len2; k++) {
            visitException(br.readUnsignedShort(), br.readUnsignedShort(), br.readUnsignedShort(), (CstClass) cp.get(br));
        }

        len2 = br.readUnsignedShort();
        visitCodeAttributes(len2);
        for (int k = 0; k < len2; k++) {
            String name1 = ((CstUTF) cp.get(br)).getString();
            int len3 = br.readInt();
            visitCodeAttribute(name1, br.readBytesDelegated(len3));
        }
        visitEndCode();
    }

    public void visitCode(int stackSize, int localSize, int len) {
        bw.writeShort(cw.getUtfId("Code"));
        bw.writeInt(0);
        codeIndex = bw.list.pos();
        bw.writeShort(stackSize).writeShort(localSize).writeInt(0);

        codeAttrAmountIndex = bw.list.pos();
    }

    public void visitBytecode(ConstantPool pool) {
        ByteReader r = new ByteReader(code);

        byte prev = 0, code;
        while (r.remain() > 0) {
            bci = r.index;
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
                    parse_table_switch(r);
                    break;
                case LOOKUPSWITCH:
                    parse_lookup_switch(r);
                    break;
                default:
                    std(code);
            }

            prev = code;
        }
    }

    public void multi_dimension_array(CstClass clz, int dimension) {
        bw.writeByte(MULTIANEWARRAY).writeShort(clz.getIndex()).writeByte((byte) dimension);
    }

    public void clazz(byte code, CstClass clz) {
        bw.writeByte(code).writeShort(clz.getIndex());
    }

    public void increase(boolean widen, int var_id, int count) {
        bw.writeByte(IINC);
        if(widen) {
            bw.writeByte((byte) var_id).writeByte((byte) count);
        } else {
            bw.writeShort(var_id).writeShort(count);
        }
    }

    public void ldc(byte code, Constant c) {
        bw.writeByte(code);
        if(code == LDC) {
            bw.writeByte((byte) c.getIndex());
        } else {
            bw.writeShort(c.getIndex());
        }
    }

    public void invoke_dynamic(CstDynamic dyn, int type) {
        bw.writeByte(INVOKEDYNAMIC).writeShort(dyn.getIndex()).writeShort(type);
    }

    public void invoke_interface(CstRefItf itf, short argc) {
        bw.writeByte(INVOKEINTERFACE).writeShort(itf.getIndex()).writeShort(argc);
    }

    public void invoke(byte code, CstRef method) {
        bw.writeByte(code).writeShort(method.getIndex());
    }

    public void field(byte code, CstRefField field) {
        bw.writeByte(code).writeShort(field.getIndex());
    }

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
        while ((r.index & 3) != 0) {
            r.index++;
        }
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
        while ((out.pos() & 3) != 0) {
            out.pos(out.pos() + 1);
        }
    }

    public void parse_lookup_switch(ByteReader r) {
        while ((r.index & 3) != 0) {
            r.index++;
        }
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
        bw.writeShort(start).writeShort(end).writeShort(handler).writeShort(cw.getClassId(type.getValue().getString()));
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

    public void visitCodeAttribute(String name, ByteList data) {
        bw.writeShort(cw.getUtfId(name)).writeInt(data.pos()).writeBytes(data);
        codeAttrAmount++;
    }

    public void visitEndCode() {
        int pos = bw.list.pos();
        bw.list.pos(codeAttrAmountIndex);
        bw.writeShort(codeAttrAmount);

        bw.list.pos(codeIndex - 4);
        bw.writeInt(pos - codeIndex);
        bw.list.pos(pos);
    }
}
