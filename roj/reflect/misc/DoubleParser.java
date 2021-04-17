package roj.reflect.misc;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/14 19:31
 */
public class DoubleParser {
    public static void main(String[] args) {
        final String v = args[0];

        boolean negative = false;

        int exp = 0;
        long data = 0;

        // IEEE 双精度格式为8字节64位，由三个字段组成：52 位小数 f ； 11 位偏置指数 e ；以及 1 位符号 s。
        // 这些字段连续存储在两个 32 位字中

        //s e f
        // value = (-1)^s * 10^(e-1023) * 1.f


        int dot = v.indexOf('.');
        if (dot == -1) {
            exp = v.length() + 1023 - 1;
            int len = Math.min(52, v.length());

            for (int i = 0; i < len; i++) {

            }
            data &= ((long) 1 << 54) - 1;
        }
    }


    public static int parseInt(String s) throws NumberFormatException {
        long result = 0;

        // 1000
        // 1 * 10 ^ 4

        int i = 0, len = Math.min(52, s.length());

        int digit;

        while (i < len) {
            if ((digit = Character.digit(s.charAt(i++), 10)) < 0)
                throw new NumberFormatException("Not a number at offset " + i + " : " + s);

            result *= 10;
            result += digit;
        }

        //System.out.println("Result = " + (negative ? -result : result));


        return (int) result;
    }
}
