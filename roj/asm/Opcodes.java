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

import roj.text.TextUtil;

// https://my.oschina.net/xionghui/blog/325563
// Office 2010 csv/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/3 15:59
 */
public final class Opcodes {
    public static final byte
            NOP = (byte) 0x00,

    // Variable Construct
    ACONST_NULL = (byte) 0x01,
            ICONST_M1 = (byte) 0x02,
            ICONST_0 = (byte) 0x03,
            ICONST_1 = (byte) 0x04,
            ICONST_2 = (byte) 0x05,
            ICONST_3 = (byte) 0x06,
            ICONST_4 = (byte) 0x07,
            ICONST_5 = (byte) 0x08,
            LCONST_0 = (byte) 0x09,
            LCONST_1 = (byte) 0x0a,
            FCONST_0 = (byte) 0x0b,
            FCONST_1 = (byte) 0x0c,
            FCONST_2 = (byte) 0x0d,
            DCONST_0 = (byte) 0x0e,
            DCONST_1 = (byte) 0x0f,

    BIPUSH = (byte) 0x10,
            SIPUSH = (byte) 0x11,
            LDC = (byte) 0x12,
            LDC_W = (byte) 0x13,
            LDC2_W = (byte) 0x14,

    // Variable Load
    ILOAD = (byte) 0x15,
            LLOAD = (byte) 0x16,
            FLOAD = (byte) 0x17,
            DLOAD = (byte) 0x18,
            ALOAD = (byte) 0x19,
            ILOAD_0 = (byte) 0x1a,
            ILOAD_1 = (byte) 0x1b,
            ILOAD_2 = (byte) 0x1c,
            ILOAD_3 = (byte) 0x1d,
            LLOAD_0 = (byte) 0x1e,
            LLOAD_1 = (byte) 0x1f,
            LLOAD_2 = (byte) 0x20,
            LLOAD_3 = (byte) 0x21,
            FLOAD_0 = (byte) 0x22,
            FLOAD_1 = (byte) 0x23,
            FLOAD_2 = (byte) 0x24,
            FLOAD_3 = (byte) 0x25,
            DLOAD_0 = (byte) 0x26,
            DLOAD_1 = (byte) 0x27,
            DLOAD_2 = (byte) 0x28,
            DLOAD_3 = (byte) 0x29,
            ALOAD_0 = (byte) 0x2a,
            ALOAD_1 = (byte) 0x2b,
            ALOAD_2 = (byte) 0x2c,
            ALOAD_3 = (byte) 0x2d,

    // ArrayGet LOAD
    IALOAD = (byte) 0x2e,
            LALOAD = (byte) 0x2f,
            FALOAD = (byte) 0x30,
            DALOAD = (byte) 0x31,
            AALOAD = (byte) 0x32,
            BALOAD = (byte) 0x33,
            CALOAD = (byte) 0x34,
            SALOAD = (byte) 0x35, 
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
    ISTORE = (byte) 0x36,
            LSTORE = (byte) 0x37,
            FSTORE = (byte) 0x38,
            DSTORE = (byte) 0x39,
            ASTORE = (byte) 0x3a, 
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

    ISTORE_0 = (byte) 0x3b,
            ISTORE_1 = (byte) 0x3c,
            ISTORE_2 = (byte) 0x3d,
            ISTORE_3 = (byte) 0x3e,
            LSTORE_0 = (byte) 0x3f,
            LSTORE_1 = (byte) 0x40,
            LSTORE_2 = (byte) 0x41,
            LSTORE_3 = (byte) 0x42,
            FSTORE_0 = (byte) 0x43,
            FSTORE_1 = (byte) 0x44,
            FSTORE_2 = (byte) 0x45,
            FSTORE_3 = (byte) 0x46,
            DSTORE_0 = (byte) 0x47,
            DSTORE_1 = (byte) 0x48,
            DSTORE_2 = (byte) 0x49,
            DSTORE_3 = (byte) 0x4a,
            ASTORE_0 = (byte) 0x4b,
            ASTORE_1 = (byte) 0x4c,
            ASTORE_2 = (byte) 0x4d,
            ASTORE_3 = (byte) 0x4e,

    IASTORE = (byte) 0x4f,
            LASTORE = (byte) 0x50,
            FASTORE = (byte) 0x51,
            DASTORE = (byte) 0x52,
            AASTORE = (byte) 0x53,
            BASTORE = (byte) 0x54,
            CASTORE = (byte) 0x55,
            SASTORE = (byte) 0x56,

    // Stack
    POP = (byte) 0X57,
            POP2 = (byte) 0X58,
            DUP = (byte) 0X59,
            DUP_X1 = (byte) 0X5A,
            DUP_X2 = (byte) 0X5B,
            DUP2 = (byte) 0X5C,
            DUP2_X1 = (byte) 0X5D,
            DUP2_X2 = (byte) 0X5E,
            SWAP = (byte) 0X5F,

    // Math
    IADD = (byte) 0X60,
            LADD = (byte) 0X61,
            FADD = (byte) 0X62,
            DADD = (byte) 0X63,
            ISUB = (byte) 0X64,
            LSUB = (byte) 0X65,
            FSUB = (byte) 0X66,
            DSUB = (byte) 0X67,
            IMUL = (byte) 0X68,
            LMUL = (byte) 0X69,
            FMUL = (byte) 0X6A,
            DMUL = (byte) 0X6B,
            IDIV = (byte) 0X6C,
            LDIV = (byte) 0X6D,
            FDIV = (byte) 0X6E,
            DDIV = (byte) 0X6F,
            IREM = (byte) 0X70,
            LREM = (byte) 0X71,
            FREM = (byte) 0X72,
            DREM = (byte) 0X73,
            INEG = (byte) 0X74,
            LNEG = (byte) 0X75,
            FNEG = (byte) 0X76,
            DNEG = (byte) 0X77,
            ISHL = (byte) 0X78,
            LSHL = (byte) 0X79,
            ISHR = (byte) 0X7A,
            LSHR = (byte) 0X7B,
            IUSHR = (byte) 0X7C,
            LUSHR = (byte) 0X7D,
            IAND = (byte) 0X7E,
            LAND = (byte) 0X7F,
            IOR = (byte) 0X80,
            LOR = (byte) 0X81,
            IXOR = (byte) 0X82,
            LXOR = (byte) 0X83,
            IINC = (byte) 0X84,
            I2L = (byte) 0X85,
            I2F = (byte) 0X86,
            I2D = (byte) 0X87,
            L2I = (byte) 0X88,
            L2F = (byte) 0X89,
            L2D = (byte) 0X8A,
            F2I = (byte) 0X8B,
            F2L = (byte) 0X8C,
            F2D = (byte) 0X8D,
            D2I = (byte) 0X8E,
            D2L = (byte) 0X8F,
            D2F = (byte) 0X90,
            I2B = (byte) 0X91,
            I2C = (byte) 0X92,
            I2S = (byte) 0X93,
            LCMP = (byte) 0X94,
            FCMPL = (byte) 0X95,
            FCMPG = (byte) 0X96,
            DCMPL = (byte) 0X97,
            DCMPG = (byte) 0X98,

    // Condition / Jump
    IFEQ = (byte) 0x99,
            IFNE = (byte) 0x9a,
            IFLT = (byte) 0x9b,
            IFGE = (byte) 0x9c,
            IFGT = (byte) 0x9d,
            IFLE = (byte) 0x9e,
            IF_icmpeq = (byte) 0x9f,
            IF_icmpne = (byte) 0xa0,
            IF_icmplt = (byte) 0xa1,
            IF_icmpge = (byte) 0xa2,
            IF_icmpgt = (byte) 0xa3,
            IF_icmple = (byte) 0xa4,
            IF_acmpeq = (byte) 0xa5,
            IF_acmpne = (byte) 0xa6,
            GOTO = (byte) 0xa7,
            JSR = (byte) 0xa8,
            RET = (byte) 0xa9,
            TABLESWITCH = (byte) 0xaa,
            LOOKUPSWITCH = (byte) 0xab,

    // Return
    IRETURN = (byte) 0xac,
            LRETURN = (byte) 0xad,
            FRETURN = (byte) 0xae,
            DRETURN = (byte) 0xaf,
            ARETURN = (byte) 0xb0,
            RETURN = (byte) 0xb1,

    // Field
    GETSTATIC = (byte) 0xb2,
            PUTSTATIC = (byte) 0xb3,
            GETFIELD = (byte) 0xb4,
            PUTFIELD = (byte) 0xb5,

    // Invoke
    INVOKEVIRTUAL = (byte) 0xb6,
            INVOKESPECIAL = (byte) 0xb7,
            INVOKESTATIC = (byte) 0xb8,
            INVOKEINTERFACE = (byte) 0xb9,
            INVOKEDYNAMIC = (byte) 0xba,

    // New
    NEW = (byte) 0xbb,
            NEWARRAY = (byte) 0xbc,
            ANEWARRAY = (byte) 0xbd,

    ARRAYLENGTH = (byte) 0xbe,
            ATHROW = (byte) 0xbf,
            CHECKCAST = (byte) 0xc0,
            INSTANCEOF = (byte) 0xc1,
            MONITORENTER = (byte) 0xc2,
            MONITOREXIT = (byte) 0xc3,
            WIDE = (byte) 0xc4,
            MULTIANEWARRAY = (byte) 0xc5,

    IFNULL = (byte) 0xc6,
            IFNONNULL = (byte) 0xc7,

    GOTO_W = (byte) 0xc8,
            JSR_W = (byte) 0xc9,

    BREAKPOINT = (byte) 0xca,
            IMPDEP1 = (byte) 0xfe,
            IMPDEP2 = (byte) 0xff;

    private static void a(int id, String cn) {
        byId[id] = "0x" + Integer.toHexString(id) + ' ' + cn;
    }

    private static final String[] byId = new String[256];

    static {
        a(0x00, "无操作");

        a(0x01, "推送null引用"); // 注意：JVM并没有为null指派一个具体的值。
        a(0x02, "推送int -1");
        a(0x03, "推送int 0");
        a(0x04, "推送int 1");
        a(0x05, "推送int 2");
        a(0x06, "推送int 3");
        a(0x07, "推送int 4");
        a(0x08, "推送int 5");
        a(0x09, "推送long 0");
        a(0x0a, "推送long 1");
        a(0x0b, "推送float 0");
        a(0x0c, "推送float 1");
        a(0x0d, "推送float 2");
        a(0x0e, "推送double 0");
        a(0x0f, "推送double 1");
        a(0x10, "推送int $0");
        a(0x11, "推送int $0");
        a(0x12, "推送常量");
        a(0x13, "推送常量");
        a(0x14, "推送常量");

        a(0x15, "加载第$0个int");
        a(0x16, "加载第$0个long");
        a(0x17, "加载第$0个float");
        a(0x18, "加载第$0个double");
        a(0x19, "加载第$0个引用");
        a(0x1a, "加载第0个int");
        a(0x1b, "加载第1个int");
        a(0x1c, "加载第2个int");
        a(0x1d, "加载第3个int");
        a(0x1e, "加载第0个long");
        a(0x1f, "加载第1个long");
        a(0x20, "加载第2个long");
        a(0x21, "加载第3个long");
        a(0x22, "加载第0个float");
        a(0x23, "加载第1个float");
        a(0x24, "加载第2个float");
        a(0x25, "加载第3个float");
        a(0x26, "加载第0个double");
        a(0x27, "加载第1个double");
        a(0x28, "加载第2个double");
        a(0x29, "加载第3个double");
        a(0x2a, "加载第0个引用");
        a(0x2b, "加载第1个引用");
        a(0x2c, "加载第2个引用");
        a(0x2d, "加载第3个引用");

        a(0x2e, "加载int型数组指定索引");
        a(0x2f, "加载long型数组指定索引");
        a(0x30, "加载float型数组指定索引");
        a(0x31, "加载double型数组指定索引");
        a(0x32, "加载引用型数组指定索引");
        a(0x33, "加载bool/byte型数组指定索引");
        a(0x34, "加载char型数组指定索引");
        a(0x35, "加载short型数组指定索引");

        a(0x36, "存储第$0个int");
        a(0x37, "存储第$0个long");
        a(0x38, "存储第$0个float");
        a(0x39, "存储第$0个double");
        a(0x3a, "存储第$0个引用");
        a(0x3b, "存储第0个int");
        a(0x3c, "存储第1个int");
        a(0x3d, "存储第2个int");
        a(0x3e, "存储第3个int");
        a(0x3f, "存储第0个long");
        a(0x40, "存储第1个long");
        a(0x41, "存储第2个long");
        a(0x42, "存储第3个long");
        a(0x43, "存储第0个float");
        a(0x44, "存储第1个float");
        a(0x45, "存储第2个float");
        a(0x46, "存储第3个float");
        a(0x47, "存储第0个double");
        a(0x48, "存储第1个double");
        a(0x49, "存储第2个double");
        a(0x4a, "存储第3个double");
        a(0x4b, "存储第0个引用");
        a(0x4c, "存储第1个引用");
        a(0x4d, "存储第2个引用");
        a(0x4e, "存储第3个引用");

        a(0x4f, "存储[I的指定索引");
        a(0x50, "存储[J的指定索引");
        a(0x51, "存储[F的指定索引");
        a(0x52, "存储[D的指定索引");
        a(0x53, "存储引用数组的指定索引");
        a(0x54, "存储bool/byte型数组的指定索引");
        a(0x55, "存储char数组的指定索引");
        a(0x56, "存储short数组的指定索引");

        a(0X57, "弹出");
        a(0X58, "弹出二位");
        a(0X59, "复制一次");
        a(0X5A, "复制两次");
        a(0X5B, "复制三次");
        a(0X5C, "复制二位一次");
        a(0X5D, "复制二位两次");
        a(0X5E, "复制二位三次");
        a(0X5F, "互换");

        a(0X60, "两INT相加");
        a(0X61, "两LONG相加");
        a(0X62, "两FLOAT相加");
        a(0X63, "两DOUBLE相加");
        a(0X64, "两INT相减");
        a(0X65, "两LONG相减");
        a(0X66, "两FLOAT相减");
        a(0X67, "两DOUBLE相减");
        a(0X68, "两INT相乘");
        a(0X69, "两LONG相乘");
        a(0X6A, "两FLOAT相乘");
        a(0X6B, "两DOUBLE相乘");
        a(0X6C, "两INT相除");
        a(0X6D, "两LONG相除");
        a(0X6E, "两FLOAT相除");
        a(0X6F, "两DOUBLE相除");
        a(0X70, "两INT取模");
        a(0X71, "两LONG取模");
        a(0X72, "两FLOAT取模");
        a(0X73, "两DOUBLE取模");

        a(0X74, "INT取负");
        a(0X75, "LONG取负");
        a(0X76, "FLOAT取负");
        a(0X77, "DOUBLE取负");

        a(0X78, "INT左移位");
        a(0X79, "LONG左移位");
        a(0X7A, "INT右（符号）移位");
        a(0X7B, "LONG右（符号）移位");
        a(0X7C, "INT右（无符号）移位");
        a(0X7D, "LONG右（无符号）移位");
        a(0X7E, "两INT与");
        a(0X7F, "两LONG与");
        a(0X80, "两INT或");
        a(0X81, "两LONG或");
        a(0X82, "两INT异或");
        a(0X83, "两LONG异或");

        a(0X84, "将INT型变量$0增加$1");

        a(0X85, "INT转LONG");
        a(0X86, "INT转FLOAT");
        a(0X87, "INT转DOUBLE");
        a(0X88, "LONG转INT");
        a(0X89, "LONG转FLOAT");
        a(0X8A, "LONG转DOUBLE");
        a(0X8B, "FLOAT转INT");
        a(0X8C, "FLOAT转LONG");
        a(0X8D, "FLOAT转DOUBLE");
        a(0X8E, "DOUBLE转INT");
        a(0X8F, "DOUBLE转LONG");
        a(0X90, "DOUBLE转FLOAT");
        a(0X91, "INT转BYTE");
        a(0X92, "INT转CHAR");
        a(0X93, "INT转SHORT");

        a(0X94, "比较两LONG,返回(1,0,-1)");
        a(0X95, "比较两FLOAT,返回(1,0,-1); 当有NaN时返回-1");
        a(0X96, "比较两FLOAT,返回(1,0,-1); 当有NaN时返回1");
        a(0X97, "比较两DOUBLE,返回(1,0,-1); 当有NaN时返回-1");
        a(0X98, "比较两DOUBLE,返回(1,0,-1); 当有NaN时返回1");

        a(0x99, "栈顶int==0时跳转");
        a(0x9a, "栈顶int!=0时跳转");
        a(0x9b, "栈顶int<0时跳转");
        a(0x9c, "栈顶int>=0时跳转");
        a(0x9d, "栈顶int>0时跳转");
        a(0x9e, "栈顶int<=0时跳转");
        a(0x9f, "比较两int,==0时跳转");
        a(0xa0, "比较两int,!=0时跳转");
        a(0xa1, "比较两int,<0时跳转");
        a(0xa2, "比较两int,>=0时跳转");
        a(0xa3, "比较两int,>0时跳转");
        a(0xa4, "比较两int,<=0时跳转");
        a(0xa5, "比较两引用,==时跳转");
        a(0xa6, "比较两引用,!=时跳转");
        a(0xa7, "跳转");

        a(0xa8, "跳转至$0,返回下一条指令地址");
        a(0xa9, "返回至变量$0保存的指令地址");

        a(0xaa, "列表switch");
        a(0xab, "二分switch");
        a(0xac, "返回int");
        a(0xad, "返回long");
        a(0xae, "返回float");
        a(0xaf, "返回double");
        a(0xb0, "返回引用");
        a(0xb1, "返回");
        a(0xb2, "获取$1的静态域$2");
        a(0xb3, "赋值$1的静态域$2");
        a(0xb4, "获取$1的实例域$2");
        a(0xb5, "赋值$1的实例域$2");
        a(0xb6, "调用动态方法");
        a(0xb7, "调用静绑方法");
        a(0xb8, "调用静态方法");
        a(0xb9, "调用接口方法");
        a(0xba, "调用动态方法");
        a(0xbb, "创建$0的对象");
        a(0xbc, "创建基本类型数组");
        a(0xbd, "创建引用类型数组");
        a(0xbe, "获得数组长度");
        a(0xbf, "抛出异常");
        a(0xc0, "强制转换为$0");
        a(0xc1, "对象是$0的实例则返回1,否则0");
        a(0xc2, "获得锁");
        a(0xc3, "释放锁");
        a(0xc4, "扩展局部变量索引");
        a(0xc5, "创建指定类型和维度的多维数组,返回其引用");
        a(0xc6, "为null时跳转");
        a(0xc7, "不为null时跳转");
        a(0xc8, "跳转");
        a(0xc9, "跳转至$0,返回下一条指令地址");
        a(0xca, "断点标记");
        a(0xfe, "软件预留");
        a(0xff, "硬件预留");
    }

    public static byte byId(byte b) {
        if (byId[b & 0xFF] == null) {
            throw new IllegalStateException("Unknown bytecode 0x" + Integer.toHexString(b & 0xFF));
        }
        return b;
    }


    public static String toString0(byte code, Object... x) {
        StringBuilder s = new StringBuilder(toString0(code));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '$') {
                int o = TextUtil.getNumber(s.charAt(i + 1));

                String ss = String.valueOf(x[o]);

                s.delete(i, i + 2).insert(i, ss);

                i += ss.length();
            }
        }

        return s.toString();
    }

    public static String toString0(byte code) {
        return byId[byId(code) & 0xFF];
    }

    @Deprecated
    public static byte valueOf(String name) {
        return 0;
    }
}