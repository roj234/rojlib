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

package roj.asm.transform;

import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/29 16:55
 */
public abstract class CodeTransformer {
    ByteList tmp = new ByteList();
    protected ByteList out = new ByteList();

    @SuppressWarnings("fallthrough")
    public ByteList parse(String clazz, ByteReader r) {
        ByteList out1 = this.tmp;
        out1.ensureCapacity(r.length());
        out1.clear();

        out1.addAll(r.getBytes(), 0, 8);
        r.index = 8;

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.read(r);
        pool.valid();

        cw = new ConstantWriter(pool);

        ByteList out2 = this.out;
        bw.list = out2;
        out2.clear();
        out2.addAll(r.getBytes(), r.index, 6);
        r.index += 6;

        int len0 = r.readUnsignedShort();
        out2.addAll(r.getBytes(), r.index - 2,  (len0 + 1) << 1);
        r.index += len0 << 1;

        int cur = r.index;

        len0 = r.readUnsignedShort();
        for (int i = 0; i < len0; i++) {
            r.index += 6;

            int attr = r.readUnsignedShort();

            for (int j = 0; j < attr; j++) {
                r.index += 2;
                int len1 = r.readInt();
                r.index += len1;
            }
        }

        out2.addAll(r.getBytes(), cur, r.index - cur + 2);

        len0 = r.readUnsignedShort();
        for (int i = 0; i < len0; i++) {
            out2.addAll(r.getBytes(), r.index, 8); // acc, name, desc, attrLen
            r.index += 2;
            String name = ((CstUTF) pool.get(r)).getString();
            String desc = ((CstUTF) pool.get(r)).getString();

            int attrLen = r.readUnsignedShort();

            for (int j = 0; j < attrLen; j++) {
                String name0 = ((CstUTF) pool.get(r)).getString();
                int len1 = r.readInt();
                if(!name0.equals("Code")) {
                    out2.addAll(r.getBytes(), r.index - 6, len1 + 6);
                } else {
                    out2.addAll(r.getBytes(), r.index - 6, 10);
                    r.index += 4;
                    code = r.readBytesDelegated(r.readInt());

                    int len2 = r.readUnsignedShort(); // exception
                    out2.addAll(r.getBytes(), r.index - 2, 2 + (len2 << 3));
                    r.index += len2 << 3;

                    len2 = r.readUnsignedShort();
                    for (int k = 0; k < len2; k++) {
                        r.index += 2;
                        int len3 = r.readInt();
                        out2.addAll(r.getBytes(), r.index - 6, len3 + 6);
                    }

                    pre_filter();
                    filter(pool);
                }
            }
        }

        out2.addAll(r.getBytes(), r.index, r.remain());
        bw.list = out1;
        cw.write(bw);
        out1.addAll(out2, 0, out2.pos());
        return out1;
    }

    protected ConstantWriter cw;
    protected ByteList code;
    protected ByteWriter bw = new ByteWriter();
    protected int bci;

    protected void pre_filter() {}

    protected void filter(ConstantPool pool) {
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

    protected void multi_dimension_array(CstClass clz, int dimension) {
        bw.writeByte(MULTIANEWARRAY).writeShort(clz.getIndex()).writeByte((byte) dimension);
    }

    protected void clazz(byte code, CstClass clz) {
        bw.writeByte(code).writeShort(clz.getIndex());
    }

    protected void increase(boolean widen, int var_id, int count) {
        bw.writeByte(IINC);
        if(widen) {
            bw.writeByte((byte) var_id).writeByte((byte) count);
        } else {
            bw.writeShort(var_id).writeShort(count);
        }
    }

    protected void ldc(byte code, Constant c) {
        bw.writeByte(code);
        if(code == LDC) {
            bw.writeByte((byte) c.getIndex());
        } else {
            bw.writeShort(c.getIndex());
        }
    }

    protected void invoke_dynamic(CstDynamic dyn, int type) {
        bw.writeByte(INVOKEDYNAMIC).writeShort(dyn.getIndex()).writeShort(type);
    }

    protected void invoke_interface(CstRefItf itf, short argc) {
        bw.writeByte(INVOKEINTERFACE).writeShort(itf.getIndex()).writeShort(argc);
    }

    protected void invoke(byte code, CstRef method) {
        bw.writeByte(code).writeShort(method.getIndex());
    }

    protected void field(byte code, CstRefField field) {
        bw.writeByte(code).writeShort(field.getIndex());
    }

    protected void jump(byte code, int offset) {
        std_u2(code, offset);
    }

    protected void jump_4(byte code, int offset) {
        std_u4(code, offset);
    }

    protected void std(byte code) {
        bw.writeByte(code);
    }

    protected void std_u1(byte code, int value) {
        bw.writeByte(code).writeByte((byte) value);
    }

    protected void std_u2(byte code, int value) {
        bw.writeByte(code).writeShort(value);
    }

    protected void std_u4(byte code, int value) {
        bw.writeByte(code).writeInt(value);
    }

    protected void parse_table_switch(ByteReader r) {
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

    protected void align_out() {
        while ((out.pos() & 3) != 0) {
            out.pos(out.pos() + 1);
        }
    }

    protected void parse_lookup_switch(ByteReader r) {
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
}