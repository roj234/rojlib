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

package roj.asm;

/**
 * Integer version of Opcodes
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/10/4 10:03
 */
public interface OpcodesInt extends Opcodes {
    int     IOR = 0X80,
            LOR = 0X81,
            IXOR = 0X82,
            LXOR = 0X83,
            IINC = 0X84,
            I2L = 0X85,
            I2F = 0X86,
            I2D = 0X87,
            L2I = 0X88,
            L2F = 0X89,
            L2D = 0X8A,
            F2I = 0X8B,
            F2L = 0X8C,
            F2D = 0X8D,
            D2I = 0X8E,
            D2L = 0X8F,
            D2F = 0X90,
            I2B = 0X91,
            I2C = 0X92,
            I2S = 0X93,
            LCMP = 0X94,
            FCMPL = 0X95,
            FCMPG = 0X96,
            DCMPL = 0X97,
            DCMPG = 0X98,

    // Condition / Jump
    IFEQ = 0x99,
            IFNE = 0x9a,
            IFLT = 0x9b,
            IFGE = 0x9c,
            IFGT = 0x9d,
            IFLE = 0x9e,
            IF_icmpeq = 0x9f,
            IF_icmpne = 0xa0,
            IF_icmplt = 0xa1,
            IF_icmpge = 0xa2,
            IF_icmpgt = 0xa3,
            IF_icmple = 0xa4,
            IF_acmpeq = 0xa5,
            IF_acmpne = 0xa6,
            GOTO = 0xa7,
            JSR = 0xa8,
            RET = 0xa9,
            TABLESWITCH = 0xaa,
            LOOKUPSWITCH = 0xab,

    // Return
    IRETURN = 0xac,
            LRETURN = 0xad,
            FRETURN = 0xae,
            DRETURN = 0xaf,
            ARETURN = 0xb0,
            RETURN = 0xb1,

    // Field
    GETSTATIC = 0xb2,
            PUTSTATIC = 0xb3,
            GETFIELD = 0xb4,
            PUTFIELD = 0xb5,

    // Invoke
    INVOKEVIRTUAL = 0xb6,
            INVOKESPECIAL = 0xb7,
            INVOKESTATIC = 0xb8,
            INVOKEINTERFACE = 0xb9,
            INVOKEDYNAMIC = 0xba,

    // New
    NEW = 0xbb,
            NEWARRAY = 0xbc,
            ANEWARRAY = 0xbd,

    ARRAYLENGTH = 0xbe,
            ATHROW = 0xbf,
            CHECKCAST = 0xc0,
            INSTANCEOF = 0xc1,
            MONITORENTER = 0xc2,
            MONITOREXIT = 0xc3,
            WIDE = 0xc4,
            MULTIANEWARRAY = 0xc5,

    IFNULL = 0xc6,
            IFNONNULL = 0xc7,

    GOTO_W = 0xc8,
            JSR_W = 0xc9,

    BREAKPOINT = 0xca,
            IMPDEP1 = 0xfe,
            IMPDEP2 = 0xff;
}