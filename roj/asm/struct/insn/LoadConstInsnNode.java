/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: LoadConstInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.constant.Constant;
import roj.asm.constant.CstDynamic;
import roj.asm.util.ConstantWriter;
import roj.asm.util.type.NativeType;
import roj.util.ByteWriter;

import static roj.asm.constant.CstType.*;

public final class LoadConstInsnNode extends InsnNode {
    @SuppressWarnings("fallthrough")
    public LoadConstInsnNode(byte code, Constant c) {
        super(code);
        if (c == null)
            throw new NullPointerException("Constant");

        /**
         * CONSTANT_Integer, CONSTANT_Float, or CONSTANT_String if the class file version number is less than 49.0.
         *
         * CONSTANT_Integer, CONSTANT_Float, CONSTANT_String, or CONSTANT_Class if the class file version number is
         * 49.0 or 50.0.
         *
         * CONSTANT_Integer, CONSTANT_Float, CONSTANT_String, CONSTANT_Class, CONSTANT_MethodType, or
         * CONSTANT_MethodHandle if the class file version number is 51.0 or above.
         */

        boolean dj = false;
        if(c.type == DYNAMIC) {
            String v = ((CstDynamic)c).getDesc().getType().getString();
            dj = v.charAt(0) == NativeType.DOUBLE || v.charAt(0) == NativeType.LONG;
        }

        switch (c.type) {
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
        this.c = c;
    }

    private static void ex(byte code, Constant c) {
        throw new IllegalArgumentException(Opcodes.toString0(code) + " can only load CLASS, STRING, DYNAMIC, " +
                "METHOD_HANDLE, METHOD_TYPE, FLOAT, DOUBLE, LONG or INT, got: " + c);
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
        super.toByteArray(w);
        if (this.code != Opcodes.LDC) {
            w.writeShort(this.cpi);
        } else {
            w.writeByte((byte) this.cpi);
        }
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        super.toByteArray(w);

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