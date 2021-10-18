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

import roj.text.TextUtil;

/**
 * @author Roj233
 * @version 0.1
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

    public static String toString0(byte code, Object... x) {
        String sx = toString0(code);
        int i = sx.indexOf('$');
        if (i == -1) return sx;
        StringBuilder s = new StringBuilder(sx);
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '$') {
                int o = TextUtil.c2i(s.charAt(i + 1));

                String ss = String.valueOf(x[o]);

                s.delete(i, i + 2).insert(i, ss);

                i += ss.length();
            }
        }

        return s.toString();
    }

    public static String toString0(byte code) {
        String x = byId[code & 0xFF];
        if (x == null) throw new IllegalStateException("Unknown bytecode 0x" + Integer.toHexString(code));
        return x;
    }

    private static void a(int id, String cn) {
        byId[id] = "0x" + Integer.toHexString(id) + ' ' + cn;
    }

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
}
