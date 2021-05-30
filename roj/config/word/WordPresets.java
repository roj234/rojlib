package roj.config.word;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/31 14:17
 */
public final class WordPresets {
    public static final short LITERAL = 1000,    // 变量名
            CHARACTER = 1001,    // 字符
            STRING = 1002,    // 字符串

    INTEGER = 1100,           // int32
            DECIMAL_D = 1101, // float64
            HEX = 1102,       // 十六进制 // format would change at Word#number
            BINARY = 1103,    // 二进制
            OCTAL = 1104,     // 八进制
            DECIMAL_F = 1105, // float32
            LONG = 1106,      // int64

    EOF = -1,      // 文件结束
            ERROR = -2;      // 出错
}
