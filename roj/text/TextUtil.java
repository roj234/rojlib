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

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.util.ByteList;

import java.io.UTFDataFormatException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
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
        sb.setLength(num);
        return sb;
    }

    public static String toFixed(double d) {
        return toFixed(d, 5);
    }

    public static String toFixed(double d, int accurate) {
        String db = Double.toString(d);
        if (db.length() > accurate) {
            int dot = db.lastIndexOf('.');
            if(dot != -1) {
                db = db.substring(0, Math.min(dot + 1 + accurate, db.length()));
            }
        }
        return db;
    }

    // 8bits: ??? ?????????????????????
    public static final int BRAILLN_CODE = 10240;

    public final static byte[] digits = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    public static final MyBitSet ASCII_CHARACTERS = MyBitSet.from(digits).addAll("*@-_+./");

    public static StringBuilder encodeURI(CharSequence src) {
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

    public static String decodeURI(CharSequence src) throws MalformedURLException {
        UTFCoder uc = IOUtil.SharedCoder.get();

        CharList cb = uc.charBuf; cb.clear();
        ByteList bb = uc.byteBuf; bb.clear();

        int len = src.length();
        int i = 0;

        while (i < len) {
            int pos = limitedIndexOf(src, '%', i, len);
            if (pos == i) {
                if (src.charAt(pos + 1) == 'u') {
                    try {
                        ByteList.decodeUTFPartial(0, 0, cb, bb);
                        bb.clear();
                    } catch (UTFDataFormatException e) {
                        throw new MalformedURLException("Malformed URI in '" + src + "' near " + i);
                    }

                    int ch = MathUtils.parseInt(src, pos + 2, pos + 6, 16);
                    cb.append((char) ch);
                    i = pos + 6;
                } else {
                    byte ch = (byte) MathUtils.parseInt(src, pos + 1, pos + 3, 16);
                    bb.put(ch);
                    i = pos + 3;
                }
            } else {
                try {
                    ByteList.decodeUTFPartial(0, 0, cb, bb);
                    bb.clear();
                } catch (UTFDataFormatException e) {
                    throw new MalformedURLException("Malformed URI in '" + src + "' near " + i);
                }

                if(pos == -1) pos = len;
                cb.append(src, i, pos);
                i = pos;
            }
        }

        try {
            ByteList.decodeUTFPartial(0, 0, cb, bb);
            bb.clear();
        } catch (UTFDataFormatException e) {
            throw new MalformedURLException("Malformed URI in '" + src + "' near " + i);
        }

        return cb.toString();
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
     * ?????????????????????
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

    public static String scaledNumber(long number) {
        if (number < 0)
            return "-" + scaledNumber(-number);
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
     * ??????????????????
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
            len = b.length - off;
            if (len <= 0) {
                off = 0;
                len = b.length;
            }
        }
        if (len <= 0) return sb;
        if ((off & 15) != 0) {
            printOff(sb, off & ~15);
            int delta = off & 15;
            while (delta-- > 0) {
                sb.append("  ");
                if ((delta & 1) != 0) {
                    sb.append(' ');
                }
            }
        } else {
            printOff(sb, off);
        }
        int d = 0;
        while (true) {
            sb.append(b2h((b[off] & 0xFF) >>> 4))
              .append(b2h(b[off++] & 0xf));
            d++;
            if (--len == 0) {
                sb.append(" ");

                int rem = 16 - d;
                rem = (rem << 1) + (rem >> 1) + 1;
                for (int i = 0; i < rem; i++) sb.append(" ");

                off -= d;
                while (d-- > 0) {
                    int j = b[off++] & 0xFF;
                    sb.append(isAsciiDisplayChar(j) ? (char) j : '.');
                }
                break;
            }
            if ((off & 1) == 0) sb.append(' ');
            if ((off & 15) == 0) {
                sb.append(" ");
                off -= 16;
                d = 0;
                for (int i = 0; i < 16; i++) {
                    int j = b[off++] & 0xFF;
                    sb.append(isAsciiDisplayChar(j) ? (char) j : '.');
                }
                printOff(sb, off);
            }
        }

        return sb;
    }

    public static boolean isAsciiDisplayChar(int j) {
        return j > 31 && j < 127;
    }

    static void printOff(StringBuilder sb, int v) {
        sb.append("\n0x");
        String s = Integer.toHexString(v);
        for (int k = 7 - s.length(); k >= 0; k--) {
            sb.append('0');
        }
        sb.append(s).append("  ");
    }

    /**
     * byte to hex
     */
    public static char b2h(int a) {
        return (char) (a < 10 ? 48 + a : (a < 16 ? 55 + a : '!'));
    }

    /**
     * char to number is represents
     */
    public static int c2i(char c) {
        if (c < 0x30 || c > 0x39) {
            return -1;
        }
        return c - 0x30;
    }

    /**
     * hex to byte
     */
    public static int h2b(char c) {
        if (c < 0x30 || c > 0x39) {
            if ((c > 64 && c < 71) || ((c = Character.toUpperCase(c)) > 64 && c < 71)) {
                return c - 55;
            }
            throw new IllegalArgumentException("Not a hex character '" + c + "'");
        }
        return c - 0x30;
    }

    public static byte[] hex2bytes(CharSequence hex) {
        return hex2bytes(hex, new ByteList()).toByteArray();
    }
    public static ByteList hex2bytes(CharSequence hex, ByteList bl) {
        int off = bl.wIndex();
        bl.ensureCapacity(off + (hex.length() >> 1));
        byte[] d = bl.list;

        for (int i = 0; i < hex.length(); ) {
            char c = hex.charAt(i++);
            if (c == ' ') continue;
            d[off++] = (byte) ((h2b(c) << 4) |
                    h2b(hex.charAt(i++)));
        }
        bl.wIndex(off);
        return bl;
    }

    public static String bytes2hex(byte[] b) {
        return bytes2hex(b, 0, b.length, new CharList()).toString();
    }
    public static CharList bytes2hex(byte[] b, int off, int len, CharList sb) {
        sb.ensureCapacity(sb.ptr + len << 1);
        len += off;
        char[] tmp = sb.list;
        int j = sb.ptr;
        while (off < len) {
            int bb = b[off++] & 0xFF;
            tmp[j++] = b2h(bb >>> 4);
            tmp[j++] = b2h(bb & 0xf);
        }
        sb.setLength(j);
        return sb;
    }

    /**
     * ???????????????
     *
     * @return -1 ??????, 0 ??????, 1 ??????
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

    public static String join(String[] args, char splitChar) {
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

    public static String join(String[] args, String splSeq) {
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
     * @implNote ???????????????
     */
    public static String[] split1(CharSequence keys, char c) {
        List<String> list = new SimpleList<>();
        return split(list, keys, c).toArray(new String[list.size()]);
    }

    public static List<String> split(CharSequence keys, char c) {
        return split(new SimpleList<>(), keys, c);
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
                if (prev < i || keepEmpty) {
                    list.add(prev == i ? "" : str.subSequence(prev, i).toString());
                    if (--max == 0) {
                        return list;
                    }
                }
                i += len;
                prev = i;
            }
            i++;
        }

        if (max > 0 && (prev < i || keepEmpty)) {
            list.add(prev == i ? "" : str.subSequence(prev, i).toString());
        }

        return list;
    }

    public static boolean safeEquals(CharSequence a, CharSequence b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = a.length() - 1; i >= 0; i--) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    public static boolean safeEqualsWithLen(CharSequence a, CharSequence b) {
        int r = a.length() == b.length() ? 1 : 0;
        for (int i = a.length() - 1; i >= 0; i--) {
            r |= a.charAt(i) ^ b.charAt(i % b.length());
        }
        return r == 0;
    }
}
