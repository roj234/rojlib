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
package roj.ui;

import org.fusesource.jansi.AnsiConsole;
import roj.util.OS;

import java.io.PrintStream;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CmdUtil {
    public enum Color {
        BLACK(30), RED(31), GREEN(32), YELLOW(33), BLUE(34), PURPLE(35), CYAN(36), WHITE(37);
        final byte id;

        Color(int char1) {
            this.id = (byte) char1;
        }
    }

    //static boolean IS_IDEA_ENV = Kernel32.INSTANCE.;
    public static final boolean ENABLE;

    static {
        boolean e = Boolean.parseBoolean(System.getProperty("cmd.enableColor", "true"));
        if (e && OS.CURRENT != OS.UNIX) {
            try {
                AnsiConsole.systemInstall();
            } catch (Throwable ignored) {
                e = false;
            }
        }
        ENABLE = e || System.getProperty("cmd.forceColor") != null;
    }

    public static boolean enabled() {
        return ENABLE;
    }

    /**
     * Pn
     * 数字参数。指定一个十进制数字。
     * Ps
     * 可选参数。指定一个十进制数，用以选择某一函数。若要指定多个函数，可用（;）分隔不同的函数。
     * PL
     * 行参数。指定一个十进制数，表示显示器或另一设备上显示的某一行。
     * Pc
     * 列参数。指定一个十进制数，表示显示器或另一设备上显示的某一列。
     * 用于光标移动、图形和键盘设置的ANSI ESC序列
     * 下列ANSI转义序列中，缩写ESC代表ASCII转义字符27（1Bh），它出现在每个转义序列的开头。
     * ESC[PL;PcH
     * 光标置位：移动光标到所指定的位置（坐标）。如果没有指定位置，则光标移至初始位置屏幕的左上角（0行，0列）。该转义序列与下面这个光标定位转义序列的工作方式相同。
     * ESC[PL;Pcf
     * 光标置位：与上面的光标定位转义序列等价。
     * ESC[PnA
     * 光标上移：向上按指定的行数移动光标，所在列数不变.如果光标已在顶行，ANSI.SYS忽略该序列。
     * ESC[PnB
     * 光标下移：按指定的行数向下移动光标数行，且保持所在列数不变.如果光标已在底行，ANSI.SYS忽略此序列。
     * ESC[PnC
     * 光标前移：按指定的列数向前移动光标数列，且保持所在行数不变。如果光标已在最右边的列上，ANSI.SYS忽略此序列。
     * ESC[PnD
     * 光标后移：按指定的列数使光标后退数列，而不改变其原所在行。如果光标已在最左列，ANSI.SYS忽略此序列。
     * ESC[s
     * 保存光标位置：保存当前光标位置。可利用“恢复光标位置”序列将光标移至此光标位置处。
     * ESC[u
     * 恢复光标位置：返回由“保存光标位置”转义序列所存放的光标位置坐标。
     * ESC[2J
     * 擦除显示：清屏并将光标移至起始位置（0行，0列）。
     * ESC[K
     * 行擦除：清除从当前光标位置到其所在行行末的所有字符（包括光标位置处的字符）。
     * ESC[Ps;...;Psm
     * 设置图形方式：下列指定的值来调用图形函数。这些指定的函数将一直起作用，直到遇到下一个同类的转义序列。图形方式改变屏幕显示的颜色和字符属性（如黑体和下划线）。
     * 文本属性
     * 0 关闭所有属性
     * 1 黑体有效
     * 4 下划线有效（仅限单色显示器）
     * 5 闪烁有效
     * 7 反相显示有效
     * 8 隐蔽
     * 前景颜色
     * 30 黑色
     * 31 红色
     * 32 绿色
     * 33 黄色
     * 34 蓝色
     * 35 洋红色
     * 36 青色
     * 37 白色
     * 背景颜色
     * 40 黑色
     * 41 红色
     * 42 绿色
     * 43 黄色
     * 44 蓝色
     * 45 洋红色
     * 46 青色
     * 47 白色
     * 参数30到47与ISO 6429标准一致。
     * ESC[=psh
     * 模式设置：改变屏宽或类型,使之成为由下列值之一所指定的模式：
     * 0 40 x 148 x 25 单色（文本）
     * 1 40 x 148 x 25 彩色（文本）
     * 2 80 x 148 x 25 单色（文本）
     * 3 80 x 148 x 25 彩色（文本）
     * 4 320 x 148 x 200 4色（图形）
     * 5 320 x 148 x 200 单色（图形）
     * 6 640 x 148 x 200 单色（图形）
     * 7 折行有效
     * 13 320 x 148 x 200 彩色（图形）
     * 14 640 x 148 x 200 彩色（16色图形）
     * 15 640 x 148 x 350 单色（2色彩图形）
     * 16 640 x 148 x 350 彩色（16色图形）
     * 17 640 x 148 x 480 单色（2色彩图形）
     * 18 640 x 148 x 480 彩色（16色图形）
     * 19 320 x 148 x 200 彩色（256色图形）
     * ESC[=Psl
     * 模式重设置：用模式设置所用的同样值进行模式重置（复位），方式7（禁止折行）除外。此转义序列的最后一个字符是小写字母l。
     * ESC[code;string;...p
     * 设置键盘字串：用一指定的串重定义键盘的键。此转义序列的参数定义如下：
     * ★ Code是下表中列出的一个或多个值。这些值代表键盘的键或键组合。在命令中用到这些值时，除了转义序列所要求的分号外，还要求输入表中所示的分号。小括号括出的代码在一些键盘中没有提供。ANS1.SYS对这些键盘，不进行括号中的代码的解释，除非在ANS1.SYS的DEVICE命令中指定了/X开关项。
     * ★ String串可以是一单个字符的ASCII码，也可以是用双引号引起的一个字串。例如，65和“A”都可用来表示大写字母A。
     * 注意：下表中的某些值并非对所有计算机都有效，注意查对你的计算机手册，看哪些值是不同的。
     */

    //interface api extends StdCallLibrary{
    //    api INSTANCE = (api) Native.loadLibrary("kernel32", api.class);
    //    int GetStdHandle(int stdHand);
    //    boolean SetConsoleTextAttribute(int hConsoleOutput, int textAtt);
    //}
    //public static void out(String str,int color){
    //    int ptr=api.INSTANCE.GetStdHandle(-11);
    //    api.INSTANCE.SetConsoleTextAttribute(ptr, color);
    //    System.out.println(str);
    //}
    public static void printColor(PrintStream stream, String string, Color foreground, boolean reset, boolean println, boolean light) {
        if (ENABLE)
            stream.print("\u001B[;" + (foreground.id + (light ? 60 : 0)) + 'm');
        if (println)
            stream.println(string);
        else
            stream.print(string);
        if (ENABLE && reset)
            stream.print("\u001B[0m");
    }

    public static void printColor(PrintStream stream, String string, Color foreground, Color bg, boolean reset, boolean println, boolean lightf, boolean lightb) {
        if (ENABLE)
            stream.print("\u001B[" + (bg.id + (lightb ? 70 : 10)) + ';' + (foreground.id + (lightf ? 60 : 0)) + 'm');
        if (println)
            stream.println(string);
        else
            stream.print(string);
        if (ENABLE && reset)
            stream.print("\u001B[0m");
    }

    public static void reset() {
        if (ENABLE)
            System.out.print("\u001B[0m");
    }

    public static void bg(Color bg) {
        bg(bg, false);
    }

    public static void bg(Color bg, boolean hl) {
        if (ENABLE)
            System.out.print("\u001B[" + (bg.id + (hl ? 70 : 10)) + 'm');
    }


    public static void cursorUpSet0(int line) {
        cursor0('F', line);
    }

    public static void cursorDownSet0(int line) {
        cursor0('E', line);
    }

    public static void cursorUp(int line) {
        cursor0('A', line);
    }

    public static void cursorDown(int line) {
        cursor0('B', line);
    }

    public static void cursorRight(int line) {
        cursor0('C', line);
    }

    public static void cursorLeft(int line) {
        cursor0('D', line);
    }

    private static void cursor0(char cr, int line) {
        if (ENABLE) {
            System.out.print(("\u001b[" + line) + cr);
        }
    }

    public static void clearLine() {
        clear0(2);
    }

    public static void clearAfter() {
        clear0(0);
    }

    public static void clearBefore() {
        clear0(1);
    }

    private static void clear0(int cat) {
        if (ENABLE)
            System.out.print("\u001b[" + cat + 'K');
    }

    public static void fg(Color fg) {
        fg(fg, false);
    }

    public static void fg(Color fg, boolean hl) {
        if (ENABLE)
            System.out.print("\u001B[" + (fg.id + (hl ? 60 : 0)) + 'm');
    }

    public static void color(String s, Color color) {
        printColor(System.out, s, color, true, false, true);
    }

    public static void colorL(String s, Color color) {
        printColor(System.out, s, color, true, true, true);
    }

    public static void info(String string) {
        info(string, true);
    }

    public static void info(String string, boolean println) {
        printColor(System.out, string, Color.WHITE, true, println, true);
    }

    public static void success(String string) {
        success(string, true);
    }

    public static void success(String string, boolean println) {
        printColor(System.out, string, Color.GREEN, true, println, true);
    }

    public static void warning(String string) {
        warning(string, true);
    }

    public static void warning(String string, boolean println) {
        printColor(System.out, string, Color.YELLOW, true, println, true);
    }

    public static void warning(String string, Throwable err) {
        printColor(System.out, string, Color.YELLOW, false, true, true);
        err.printStackTrace(System.out);
        reset();
    }

    public static void error(String string) {
        error(string, true);
    }

    public static void error(String string, boolean println) {
        printColor(System.out, string, Color.RED, true, println, true);
    }

    public static void error(String string, Throwable err) {
        printColor(System.out, string, Color.RED, false, true, true);
        err.printStackTrace(System.out);
        reset();
    }
}
