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

package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.Constant;
import roj.asm.cst.CstDynamic;
import roj.asm.type.NativeType;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

import static roj.asm.cst.CstType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class LoadConstInsnNode extends InsnNode {
    @SuppressWarnings("fallthrough")
    public LoadConstInsnNode(byte code, Constant c) {
        super(code);
        if (c == null)
            throw new NullPointerException("Constant");

        _verify(code, c);
        this.c = c;
    }

    public static void _verify(byte code, Constant c) {
        boolean dj = false;
        if(c.type() == DYNAMIC) {
            String v = ((CstDynamic) c).getDesc().getType().getString();
            dj = v.charAt(0) == NativeType.DOUBLE || v.charAt(0) == NativeType.LONG;
        }

        switch (c.type()) {
            case STRING:
            case FLOAT:
            case INT:
            case CLASS:
            case METHOD_HANDLE:
            case METHOD_TYPE:
                break;
            case DYNAMIC:
                if ((code == Opcodes.LDC2_W) != dj)
                    ex(code, c);
                break;
            case LONG:
            case DOUBLE:
                if (code != Opcodes.LDC2_W) {
                    ex(code, c);
                }
                break;
            default:
                ex(code, c);
        }
    }

    @Override
    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
        _verify(code, c);
        /**
         * The constant pool entry referenced by that index must be of type:
         *
         * Int, Float, or String if main < 49
         *
         * Int, Float, String, or Class if main in 49, 50
         *
         * Int, Float, String, Class, MethodType, or MethodHandle if main >= 51
         */
         switch (c.type()) {
             case INT:
             case FLOAT:
             case STRING:
             case LONG:
             case DOUBLE:
                 break;
             case CLASS:
                 if(mainVer < 49)
                     throw new IllegalArgumentException("Constant " + c + " is not loadable at version " + mainVer);
                 break;
             case METHOD_TYPE:
             case METHOD_HANDLE:
                 if(mainVer < 51)
                     throw new IllegalArgumentException("Constant " + c + " is not loadable at version " + mainVer);
                 break;
             case DYNAMIC:
                 if(mainVer < 55)
                     throw new IllegalArgumentException("Constant " + c + " is not loadable at version " + mainVer);
                 break;
         }
    }

    private static void ex(byte code, Constant c) {
        throw new IllegalArgumentException(Opcodes.toString0(code) + " can only load " + (code == Opcodes.LDC2_W ? "DOUBLE or LONG" : "CLASS, STRING, DYNAMIC, " +
                "METHOD_HANDLE, METHOD_TYPE, FLOAT or INT") + ", got: " + c);
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case Opcodes.LDC:
            case Opcodes.LDC_W:
            case Opcodes.LDC2_W:
                return true;
            default:
                return false;
        }
    }

    public Constant c;
    private int cpi;

    @Override
    public void toByteArray(ByteWriter w) {
        w.writeByte(code);
        if (this.code != Opcodes.LDC) {
            w.writeShort(this.cpi);
        } else {
            w.writeByte((byte) this.cpi);
        }
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeByte(code);

        if ((this.cpi = (c = pool.reset(c)).getIndex()) == 0) {
            throw new NullPointerException("Invalid constant: " + pool.reset(c));
        }

        if (this.code == Opcodes.LDC2_W || (this.code = (cpi == (byte) cpi) ? Opcodes.LDC : Opcodes.LDC_W) != Opcodes.LDC) {
            w.writeShort(cpi);
        } else {
            w.writeByte((byte) cpi);
        }
    }

    public String toString() {
        return super.toString() + ' ' + c;
    }
}