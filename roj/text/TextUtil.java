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
package roj.text;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.math.MathUtils;
import roj.util.ByteList;

import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/6/19 0:14
 */
public class TextUtil {
    public static Map<String, String> parseLang(CharSequence content) {
        MyHashMap<String, String> map = new MyHashMap<>();
        if (content == null) return map;
        try {
            boolean block = false;
            CharList sb = new CharList();
            List<String> k_v = new ArrayList<>();
            for (String entry : new SimpleLineReader(content)) {
                if(entry.startsWith("#") || entry.isEmpty()) continue;
                if (!block) {
                    k_v.clear();
                    split(k_v, entry, '=', 2);
                    if (k_v.get(1).startsWith("#strl")) {
                        block = true;
                        sb.clear();
                        sb.append(k_v.get(1).substring(5));
                    } else {
                        map.put(k_v.get(0), k_v.get(1));
                    }
                } else {
                    if (entry.equals("#endl")) {
                        block = false;
                        map.put(k_v.get(0), sb.toString());
                        sb.clear();
                    } else {
                        sb.append(entry).append('\n');
                    }
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return map;
    }

    public static CharList repeat(int num, char ch) {
        if(num <= 0) return new CharList();
        CharList sb = new CharList(num);
        Arrays.fill(sb.list, ch);
        sb.setIndex(num);
        return sb;
    }

    public static String scaledDouble(double d) {
        return scaledDouble(d, 5);
    }

    public static String scaledDouble(double d, int accurate) {
        String db = Double.toString(d);
        if (db.length() > 5) {
            int dot = db.lastIndexOf('.');
            if(dot != -1) {
                db = db.substring(0, Math.min(dot + 1 + accurate, db.length()));
            }
        }
        return db;
    }

    public final static byte[] digits = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    public static final IBitSet ASCII_CHARACTERS = LongBitSet.from(digits).addAll("*@-_+./");

    public static StringBuilder escape(CharSequence src) {
        StringBuilder tmp = new StringBuilder(src.length() * 3);
        for (int i = 0; i < src.length(); i++) {
            char j = src.charAt(i);
            if (ASCII_CHARACTERS.contains(j)) {
                tmp.append(j);
            } else if (j < 0x100) {
                tmp.append("%");
                if (j < 0x10)
                    tmp.append("0");
                tmp.append(Integer.toString(j, 16));
            } else {
                tmp.append("%u").append(Integer.toString(j, 16));
            }
        }
        return tmp;
    }

    public static String unescapeBytes(final CharSequence src) {
        int len;
        ByteList tmp = new ByteList((len = src.length()) >> 1);
        int i = 0, pos;

        while (i < len) {
            pos = limitedIndexOf(src, '%', i, len);
            if (pos == i) {
                if (src.charAt(pos + 1) == 'u') {
                    throw new IllegalStateException();
                } else {
                    byte ch = (byte) MathUtils.parseInt(src, pos + 1, pos + 3, 16);
                    tmp.put(ch);
                    i = pos + 3;
                }
            } else {
                if(pos == -1) {
                    pos = len;
                }
                while (i < pos) {
                    tmp.put((byte) src.charAt(i));
                    i++;
                }
            }
        }
        try {
            return ByteList.readUTF(tmp);
        } catch (UTFDataFormatException e) {
            e.printStackTrace();
        }
        return "-";
    }

    public static int lastMatches(CharSequence a, int aIndex, CharSequence b, int bIndex, int max) {
        int min = Math.min(Math.min(a.length() - aIndex, b.length() - bIndex), max);
        int i = 0;
        for (; i < min; i++) {
            if (a.charAt(aIndex++) != b.charAt(bIndex++))
                break;
        }

        return i;
    }

    public static boolean regionMatches(CharSequence a, int aIndex, CharSequence b, int bIndex) {
        int min = Math.min(a.length() - aIndex, b.length() - bIndex);
        for (; min > 0; min--) {
            if (a.charAt(aIndex++) != b.charAt(bIndex++))
                return false;
        }

        return true;
    }

    /**
     * 这个字是中文吗
     *
     * @return True if is
     */
    public static boolean isChinese(int c) {
        return (c >= 0x4E00 && c <= 0x9FFF) // 4E00..9FFF
                || (c >= 0xF900 && c <= 0xFAFF) // F900..FAFF
                || (c >= 0x3400 && c <= 0x4DBF) // 3400..4DBF
                || (c >= 0x2000 && c <= 0x206F) // 2000..206F
                || (c >= 0x3000 && c <= 0x303F) // 3000..303F
                || (c >= 0xFF00 && c <= 0xFFEF); // FF00..FFEF
    }

    public static String getScaledNumber(long number) {
        if (number < 0)
            return "-" + getScaledNumber(-number);
        if (number >= 1000000) {
            if (number >= 1000000000) {
                return String.valueOf(number / 1000000000) + '.' + number % 1000000000 / 10000000 + 'G';
            }
            return String.valueOf(number / 1000000) + '.' + number % 1000000 / 10000 + 'M';
        } else {
            if (number >= 1000) {
                return String.valueOf(number / 1000) + '.' + number % 1000 / 10 + 'k';
            }
            return String.valueOf(number);
        }
    }

    /**
     * 找到首个字符
     */
    public static int firstCap(CharSequence str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    public static String dumpBytes(byte[] b) {
        return dumpBytes(new StringBuilder(), b, 0, b.length).toString();
    }

    public static String dumpBytes(byte[] b, int off, int len) {
        return dumpBytes(new StringBuilder(), b, off, len).toString();
    }

    public static StringBuilder dumpBytes(StringBuilder sb, byte[] b, int off, int len) {
        if (b.length - off < len) {
            System.err.println("Index out of bounds: len+" + (len - b.length + off));
            len = b.length - off;
        }
        off &= ~31;
        printOff(sb, off);
        while (true) {
            sb.append(i2h_char((b[off] >>> 4) & 0xf))
              .append(i2h_char(b[off++] & 0xf));
            if (off == len) break;
            if ((off & 1) == 0) sb.append(' ');
            if ((off & 15) == 0) printOff(sb, off);
        }

        return sb;
    }

    static void printOff(StringBuilder sb, int v) {
        sb.append("\n0x");
        String s = Integer.toHexString(v);
        for (int k = 7 - s.length(); k >= 0; k--) {
            sb.append('0');
        }
        sb.append(s).append("   ");
    }

    public static char i2h_char(int a) {
        return (char) (a < 10 ? 48 + a : (a < 16 ? 55 + a : '!'));
    }

    public static int c2i(char c) {
        if (c < 0x30 || c > 0x39) {
            return -1;
        }
        return c - 0x30;
    }

    public static int c2i_hex(char c) {
        if (c < 0x30 || c > 0x39) {
            return (c > 55 && c < 71) || ((c = Character.toLowerCase(c)) > 55 && c < 71) ? c - 45 : -1;
        }
        return c - 0x30;
    }

    /**
     * 是否为数字
     *
     * @return -1 不是, 0 整数, 1 小数
     */
    public static int isNumber(CharSequence s) {
        if (s == null || s.length() == 0)
            return -1;
        boolean dot = false;
        int i = 0;
        int off = 0;

        if (s.charAt(0) == '+' || s.charAt(0) == '-')
            if (s.length() == 1)
                return -1;
            else
                off = i = 1;

        for (int len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (c == 0x2E && !dot && i != off && i != len - 1) {
                dot = true;
            } else if (c < 0x30 || c > 0x39) {
                return -1;
            }
        }

        return dot || (!checkInt(INT_MAXS, s, off, s.charAt(0) == '-')) ? 1 : 0;
    }

    public static final byte[] INT_MAXS = new byte[]{
            '2', '1', '4', '7', '4', '8', '3', '6', '4', '8'
    };

    public static final byte[] LONG_MAXS = new byte[]{
            '9','2','2','3','3','7','2','0','3','6','8','5','4','7','7','5','8','0','8'
    };

    public static boolean checkInt(byte[] maxs, CharSequence s, int off, boolean negative) {
        int k = maxs.length + off;
        if (s.length() > k)
            return false;
        if (s.length() < k)
            return true;
        // s.length = k
        for (int i = off; i < k; i++) {
            if (s.charAt(i) > maxs[i - off]) {
                return false;
            }
        }
        if (!negative) {
            return s.charAt(k - 1) < maxs[maxs.length - 1];
        }
        return true;
    }

    public static String prettyPrint(Object o) {
        StringBuilder sb = new StringBuilder();
        prettyPrint(sb, o, "");
        return sb.toString();
    }

    private static void prettyPrint(StringBuilder sb, Object o, CharSequence off) {
        String off2 = off + "  ";
        try {
            if (o instanceof Iterable) {
                Iterable<?> itr = (Iterable<?>) o;
                sb.append(off).append("[").append('\n');
                for (Object o1 : itr) {
                    prettyPrint(sb, o1, off2);
                    sb.append('\n');
                }
                sb.append(']').append('\n');
            } else if (o instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) o;
                sb.append(off).append("[").append('\n');
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    prettyPrint(sb, entry.getKey(), off2);
                    sb.append(" = ");
                    prettyPrint(sb, entry.getValue(), off2);
                }
                sb.append(']').append('\n');
            } else {
                sb.append(off).append(o);
            }
        } catch (StackOverflowError ignored) {
        }
    }

    public static String concat(String[] args, char splitChar) {
        if (args.length == 0) return "";
        int i = args.length - 1;
        for (String arg : args) {
            i += arg.length();
        }
        char[] tmp = new char[i];
        i = 0;
        for (String s : args) {
            s.getChars(0, s.length(), tmp, i);
            i += s.length();
            if (i != tmp.length) {
                tmp[i++] = splitChar;
            }
        }
        return new String(tmp);
    }

    public static String concat(String[] args, String splSeq) {
        if (args.length == 0) return "";
        int i = splSeq.length() * (args.length - 1);
        for (String arg : args) {
            i += arg.length();
        }
        char[] tmp = new char[i];
        i = 0;
        for (String s : args) {
            s.getChars(0, s.length(), tmp, i);
            i += s.length();
            if (i != tmp.length) {
                splSeq.getChars(0, splSeq.length(), tmp, i);
                i += splSeq.length();
            }
        }
        return new String(tmp);
    }

    public static void replaceVariable(Map<String, String> env, String tag, List<String> list) {
        try {
            Tokenizer lexer = new Tokenizer().init(tag);
            while (lexer.hasNext()) {
                Word word = lexer.readStringToken();
                String val = word.val();
                if(word.type() == Tokenizer.SYMBOL) {
                    // =
                    word = lexer.readStringToken();
                    if(word.type() == WordPresets.EOF) {
                        list.set(list.size() - 1, list.get(list.size() - 1) + '=');
                        break;
                    }

                    val = word.val();
                    if (val.startsWith("${") && val.endsWith("}")) {
                        val = env.getOrDefault(val.substring(2, val.length() - 1), val);
                    }

                    list.set(list.size() - 1, list.get(list.size() - 1) + '=' + val);
                } else {
                    if (val.startsWith("${") && val.endsWith("}")) {
                        val = env.getOrDefault(val.substring(2, val.length() - 1), val);
                    }

                    if(word.type() != WordPresets.EOF)
                        list.add(val);
                    else
                        break;
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static int limitedIndexOf(CharSequence val, char ch, int off, int max) {
        for (int i = off, m = Math.min(val.length(), max); i < m; i++) {
            if (val.charAt(i) == ch)
                return i;
        }
        return -1;
    }

    public static int limitedIndexOf(CharSequence val, char ch, int max) {
        return limitedIndexOf(val, ch, 0, max);
    }

    public static int lastIndexOf(CharSequence val, char ch) {
        for (int i = val.length() - 1; i >= 0; i--) {
            if (val.charAt(i) == ch)
                return i;
        }
        return -1;
    }

    public static int limitedLastIndexOf(CharSequence val, char ch, int max) {
        for (int i = val.length() - 1, low = Math.max(0, i - max); i >= low; i--) {
            if (val.charAt(i) == ch)
                return i;
        }
        return -1;
    }

    /**
     * @implNote 忽略空字符
     */
    public static String[] split(String keys, char c) {
        List<String> list = new ArrayList<>();
        return split(list, keys, c).toArray(new String[list.size()]);
    }

    public static List<String> split(List<String> list, CharSequence str, char delimiter) {
        return split(list, str, delimiter, Integer.MAX_VALUE, false);
    }

    public static List<String> split(List<String> list, CharSequence str, char delimiter, int max) {
        return split(list, str, delimiter, max, false);
    }

    public static List<String> split(List<String> list, CharSequence str, char delimiter, int max, boolean keepEmpty) {
        int i = 0, prev = 0;
        while (i < str.length()) {
            if (delimiter == str.charAt(i)) {
                if (prev < i || keepEmpty) {
                    list.add(prev == i ? "" : str.subSequence(prev, i).toString());
                    if (--max == 0) {
                        return list;
                    }
                }
                prev = i + 1;
            }
            i++;
        }

        if (max > 0 && (prev < i || keepEmpty)) {
            list.add(prev == i ? "" : str.subSequence(prev, i).toString());
        }

        return list;
    }

    public static List<String> split(List<String> list, CharSequence str, CharSequence delimiter) {
        return split(list, str, delimiter, Integer.MAX_VALUE, false);
    }

    public static List<String> split(List<String> list, CharSequence str, CharSequence delimiter, int max, boolean keepEmpty) {
        switch (delimiter.length()) {
            case 0:
                for (int i = 0; i < str.length(); i++) {
                    list.add(String.valueOf(str.charAt(i)));
                }
                return list;
            case 1:
                return split(list, str, delimiter.charAt(0), max, keepEmpty);
        }

        char first = delimiter.charAt(0);

        int len = delimiter.length();
        int i = 0, prev = 0;
        while (i < str.length()) {
            if (first == str.charAt(i) &&
                    lastMatches(str, i, delimiter, 0, len) == len) {
                i += len;
                if (prev < i || keepEmpty) {
                    list.add(prev == i ? "" : str.subSequence(prev, i - len + 1).toString());
                    if (--max == 0) {
                        return list;
                    }
                }
                prev = i;
            }
            i++;
        }

        if (max > 0 && (prev < i || keepEmpty)) {
            list.add(prev == i ? "" : str.subSequence(prev, i - len + 1).toString());
        }

        return list;
    }
}
