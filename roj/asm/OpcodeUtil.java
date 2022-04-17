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
package roj.asm;

/**
 * @author Roj233
 * @since 2021/10/19 19:05
 */
public final class OpcodeUtil {
    private static final String[] byId = new String[256];

    public static byte byId(byte b) {
        if (byId[b & 0xFF] == null) {
            throw new IllegalStateException("Unknown bytecode 0x" + Integer.toHexString(b & 0xFF));
        }
        return b;
    }

    public static String toString0(byte code) {
        String x = byId[code & 0xFF];
        if (x == null) throw new IllegalStateException("Unknown bytecode 0x" + Integer.toHexString(code));
        return x;
    }

    private static void a(int id, String cn) {
        byId[id&0xff] = cn;
    }

    static {
        a(0,"NOP");
        a(1,"ACONST_NULL");
        a(2,"ICONST_M1");
        a(3,"ICONST_0");
        a(4,"ICONST_1");
        a(5,"ICONST_2");
        a(6,"ICONST_3");
        a(7,"ICONST_4");
        a(8,"ICONST_5");
        a(9,"LCONST_0");
        a(10,"LCONST_1");
        a(11,"FCONST_0");
        a(12,"FCONST_1");
        a(13,"FCONST_2");
        a(14,"DCONST_0");
        a(15,"DCONST_1");
        a(16,"BIPUSH");
        a(17,"SIPUSH");
        a(18,"LDC");
        a(19,"LDC_W");
        a(20,"LDC2_W");
        a(21,"ILOAD");
        a(22,"LLOAD");
        a(23,"FLOAD");
        a(24,"DLOAD");
        a(25,"ALOAD");
        a(26,"ILOAD_0");
        a(27,"ILOAD_1");
        a(28,"ILOAD_2");
        a(29,"ILOAD_3");
        a(30,"LLOAD_0");
        a(31,"LLOAD_1");
        a(32,"LLOAD_2");
        a(33,"LLOAD_3");
        a(34,"FLOAD_0");
        a(35,"FLOAD_1");
        a(36,"FLOAD_2");
        a(37,"FLOAD_3");
        a(38,"DLOAD_0");
        a(39,"DLOAD_1");
        a(40,"DLOAD_2");
        a(41,"DLOAD_3");
        a(42,"ALOAD_0");
        a(43,"ALOAD_1");
        a(44,"ALOAD_2");
        a(45,"ALOAD_3");
        a(46,"IALOAD");
        a(47,"LALOAD");
        a(48,"FALOAD");
        a(49,"DALOAD");
        a(50,"AALOAD");
        a(51,"BALOAD");
        a(52,"CALOAD");
        a(53,"SALOAD");
        a(54,"ISTORE");
        a(55,"LSTORE");
        a(56,"FSTORE");
        a(57,"DSTORE");
        a(58,"ASTORE");
        a(59,"ISTORE_0");
        a(60,"ISTORE_1");
        a(61,"ISTORE_2");
        a(62,"ISTORE_3");
        a(63,"LSTORE_0");
        a(64,"LSTORE_1");
        a(65,"LSTORE_2");
        a(66,"LSTORE_3");
        a(67,"FSTORE_0");
        a(68,"FSTORE_1");
        a(69,"FSTORE_2");
        a(70,"FSTORE_3");
        a(71,"DSTORE_0");
        a(72,"DSTORE_1");
        a(73,"DSTORE_2");
        a(74,"DSTORE_3");
        a(75,"ASTORE_0");
        a(76,"ASTORE_1");
        a(77,"ASTORE_2");
        a(78,"ASTORE_3");
        a(79,"IASTORE");
        a(80,"LASTORE");
        a(81,"FASTORE");
        a(82,"DASTORE");
        a(83,"AASTORE");
        a(84,"BASTORE");
        a(85,"CASTORE");
        a(86,"SASTORE");
        a(87,"POP");
        a(88,"POP2");
        a(89,"DUP");
        a(90,"DUP_X1");
        a(91,"DUP_X2");
        a(92,"DUP2");
        a(93,"DUP2_X1");
        a(94,"DUP2_X2");
        a(95,"SWAP");
        a(96,"IADD");
        a(97,"LADD");
        a(98,"FADD");
        a(99,"DADD");
        a(100,"ISUB");
        a(101,"LSUB");
        a(102,"FSUB");
        a(103,"DSUB");
        a(104,"IMUL");
        a(105,"LMUL");
        a(106,"FMUL");
        a(107,"DMUL");
        a(108,"IDIV");
        a(109,"LDIV");
        a(110,"FDIV");
        a(111,"DDIV");
        a(112,"IREM");
        a(113,"LREM");
        a(114,"FREM");
        a(115,"DREM");
        a(116,"INEG");
        a(117,"LNEG");
        a(118,"FNEG");
        a(119,"DNEG");
        a(120,"ISHL");
        a(121,"LSHL");
        a(122,"ISHR");
        a(123,"LSHR");
        a(124,"IUSHR");
        a(125,"LUSHR");
        a(126,"IAND");
        a(127,"LAND");
        a(-128,"IOR");
        a(-127,"LOR");
        a(-126,"IXOR");
        a(-125,"LXOR");
        a(-124,"IINC");
        a(-123,"I2L");
        a(-122,"I2F");
        a(-121,"I2D");
        a(-120,"L2I");
        a(-119,"L2F");
        a(-118,"L2D");
        a(-117,"F2I");
        a(-116,"F2L");
        a(-115,"F2D");
        a(-114,"D2I");
        a(-113,"D2L");
        a(-112,"D2F");
        a(-111,"I2B");
        a(-110,"I2C");
        a(-109,"I2S");
        a(-108,"LCMP");
        a(-107,"FCMPL");
        a(-106,"FCMPG");
        a(-105,"DCMPL");
        a(-104,"DCMPG");
        a(-103,"IFEQ");
        a(-102,"IFNE");
        a(-101,"IFLT");
        a(-100,"IFGE");
        a(-99,"IFGT");
        a(-98,"IFLE");
        a(-97,"IF_icmpeq");
        a(-96,"IF_icmpne");
        a(-95,"IF_icmplt");
        a(-94,"IF_icmpge");
        a(-93,"IF_icmpgt");
        a(-92,"IF_icmple");
        a(-91,"IF_acmpeq");
        a(-90,"IF_acmpne");
        a(-89,"GOTO");
        a(-88,"JSR");
        a(-87,"RET");
        a(-86,"TABLESWITCH");
        a(-85,"LOOKUPSWITCH");
        a(-84,"IRETURN");
        a(-83,"LRETURN");
        a(-82,"FRETURN");
        a(-81,"DRETURN");
        a(-80,"ARETURN");
        a(-79,"RETURN");
        a(-78,"GETSTATIC");
        a(-77,"PUTSTATIC");
        a(-76,"GETFIELD");
        a(-75,"PUTFIELD");
        a(-74,"INVOKEVIRTUAL");
        a(-73,"INVOKESPECIAL");
        a(-72,"INVOKESTATIC");
        a(-71,"INVOKEINTERFACE");
        a(-70,"INVOKEDYNAMIC");
        a(-69,"NEW");
        a(-68,"NEWARRAY");
        a(-67,"ANEWARRAY");
        a(-66,"ARRAYLENGTH");
        a(-65,"ATHROW");
        a(-64,"CHECKCAST");
        a(-63,"INSTANCEOF");
        a(-62,"MONITORENTER");
        a(-61,"MONITOREXIT");
        a(-60,"WIDE");
        a(-59,"MULTIANEWARRAY");
        a(-58,"IFNULL");
        a(-57,"IFNONNULL");
        a(-56,"GOTO_W");
        a(-55,"JSR_W");
        a(-54,"BREAKPOINT");
        a(-2,"IMPDEP1");
        a(-1,"IMPDEP2");
    }
}
