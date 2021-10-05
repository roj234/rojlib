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
public final class OpcodesInt {
    public static final int
            NOP = 0x00,

    // Variable Construct
    ACONST_NULL = 0x01,
            ICONST_M1 = 0x02,
            ICONST_0 = 0x03,
            ICONST_1 = 0x04,
            ICONST_2 = 0x05,
            ICONST_3 = 0x06,
            ICONST_4 = 0x07,
            ICONST_5 = 0x08,
            LCONST_0 = 0x09,
            LCONST_1 = 0x0a,
            FCONST_0 = 0x0b,
            FCONST_1 = 0x0c,
            FCONST_2 = 0x0d,
            DCONST_0 = 0x0e,
            DCONST_1 = 0x0f,

    BIPUSH = 0x10,
            SIPUSH = 0x11,
            LDC = 0x12,
            LDC_W = 0x13,
            LDC2_W = 0x14,

    // Variable Load
    ILOAD = 0x15,
            LLOAD = 0x16,
            FLOAD = 0x17,
            DLOAD = 0x18,
            ALOAD = 0x19,
            ILOAD_0 = 0x1a,
            ILOAD_1 = 0x1b,
            ILOAD_2 = 0x1c,
            ILOAD_3 = 0x1d,
            LLOAD_0 = 0x1e,
            LLOAD_1 = 0x1f,
            LLOAD_2 = 0x20,
            LLOAD_3 = 0x21,
            FLOAD_0 = 0x22,
            FLOAD_1 = 0x23,
            FLOAD_2 = 0x24,
            FLOAD_3 = 0x25,
            DLOAD_0 = 0x26,
            DLOAD_1 = 0x27,
            DLOAD_2 = 0x28,
            DLOAD_3 = 0x29,
            ALOAD_0 = 0x2a,
            ALOAD_1 = 0x2b,
            ALOAD_2 = 0x2c,
            ALOAD_3 = 0x2d,

    // ArrayGet LOAD
    IALOAD = 0x2e,
            LALOAD = 0x2f,
            FALOAD = 0x30,
            DALOAD = 0x31,
            AALOAD = 0x32,
            BALOAD = 0x33,
            CALOAD = 0x34,
            SALOAD = 0x35, 
/*
指令格式：aALOAD

功能描述：栈顶的数组下标（index）、数组引用（arrayref）出栈,并根据这两个数值取出对应的数组元素值（value）进栈。

抛出异常：如果arrayref的值为null,会抛出NullPointerException。如果index造成数组越界,会抛出ArrayIndexOutOfBoundsException。

指令执行前  指令执行后
栈底
...         ...
arrayref    value
index
栈顶

index      ：  int类型
arrayref   ：  数组的引用


*/

    // Variable Store
    ISTORE = 0x36,
            LSTORE = 0x37,
            FSTORE = 0x38,
            DSTORE = 0x39,
            ASTORE = 0x3a, 
/*
指令格式：ASTORE index

功能描述：将栈顶数值（引用ref）存入当前frame的局部变量数组中指定下标（index）处的变量中,栈顶数值出栈。

指令执行前 指令执行后
栈底
...        ...
引用ref
栈顶

index：无符号一byte整数。该指令和wide联用,index可以为无符号两byte整数。
*/

    ISTORE_0 = 0x3b,
            ISTORE_1 = 0x3c,
            ISTORE_2 = 0x3d,
            ISTORE_3 = 0x3e,
            LSTORE_0 = 0x3f,
            LSTORE_1 = 0x40,
            LSTORE_2 = 0x41,
            LSTORE_3 = 0x42,
            FSTORE_0 = 0x43,
            FSTORE_1 = 0x44,
            FSTORE_2 = 0x45,
            FSTORE_3 = 0x46,
            DSTORE_0 = 0x47,
            DSTORE_1 = 0x48,
            DSTORE_2 = 0x49,
            DSTORE_3 = 0x4a,
            ASTORE_0 = 0x4b,
            ASTORE_1 = 0x4c,
            ASTORE_2 = 0x4d,
            ASTORE_3 = 0x4e,

    IASTORE = 0x4f,
            LASTORE = 0x50,
            FASTORE = 0x51,
            DASTORE = 0x52,
            AASTORE = 0x53,
            BASTORE = 0x54,
            CASTORE = 0x55,
            SASTORE = 0x56,

    // Stack
    POP = 0X57,
            POP2 = 0X58,
            DUP = 0X59,
            DUP_X1 = 0X5A,
            DUP_X2 = 0X5B,
            DUP2 = 0X5C,
            DUP2_X1 = 0X5D,
            DUP2_X2 = 0X5E,
            SWAP = 0X5F,

    // Math
    IADD = 0X60,
            LADD = 0X61,
            FADD = 0X62,
            DADD = 0X63,
            ISUB = 0X64,
            LSUB = 0X65,
            FSUB = 0X66,
            DSUB = 0X67,
            IMUL = 0X68,
            LMUL = 0X69,
            FMUL = 0X6A,
            DMUL = 0X6B,
            IDIV = 0X6C,
            LDIV = 0X6D,
            FDIV = 0X6E,
            DDIV = 0X6F,
            IREM = 0X70,
            LREM = 0X71,
            FREM = 0X72,
            DREM = 0X73,
            INEG = 0X74,
            LNEG = 0X75,
            FNEG = 0X76,
            DNEG = 0X77,
            ISHL = 0X78,
            LSHL = 0X79,
            ISHR = 0X7A,
            LSHR = 0X7B,
            IUSHR = 0X7C,
            LUSHR = 0X7D,
            IAND = 0X7E,
            LAND = 0X7F,
            IOR = 0X80,
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