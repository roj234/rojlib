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
import roj.collect.IntMap;
import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.math.MathUtils;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.log.Logger;

import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/19 0:14
 */
public class TextUtil {
    @Deprecated
    private static final IntMap<Map<String, String>> langs = new IntMap<>();

    @Deprecated
    public static String translate(int type, String key) {
        Map<String, String> map = langs.get(type);
        if (map == null) {
            Logger.getLogger("TextUtil").warn("LangId " + type + " not found");
            return key;
        }
        return map.getOrDefault(key, key);
    }

    @Deprecated
    public static void loadLang(int type, String string) {
        langs.put(type, loadLang(string));
    }

    public static Map<String, String> loadLang(CharSequence content) {
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
                    split(k_v, sb, entry, '=', 2);
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
                db = db.substring(0, dot + 1 + accurate);
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
                    tmp.add(ch);
                    i = pos + 3;
                }
            } else {
                while (i < pos) {
                    tmp.add((byte) src.charAt(i));
                    i++;
                }
            }
        }
        try {
            return ByteReader.readUTF(tmp);
        } catch (UTFDataFormatException e) {
            e.printStackTrace();
        }
        return "-";
    }

    public static StringBuilder unescape(final CharSequence src) {
        int len;
        StringBuilder tmp = new StringBuilder((len = src.length()) >> 1);
        int i = 0, pos;

        while (i < len) {
            pos = limitedIndexOf(src, '%', i, len);
            if (pos == i) {
                if (src.charAt(pos + 1) == 'u') {
                    int ch = MathUtils.parseInt(src, pos + 2, pos + 6, 16);

                    tmp.append((char) ch);

                    i = pos + 6;
                } else {
                    char ch = (char) MathUtils.parseInt(src, pos + 1, pos + 3, 16);
                    tmp.append(ch);
                    i = pos + 3;
                }
            } else if (pos == -1) {
                return tmp.append(src.subSequence(i, src.length()));
            } else {
                tmp.append(src, i, pos);
                i = pos;
            }
        }
        return tmp;
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

    public static String dumpBytes(byte[] b, int len) {
        return dumpBytes(new StringBuilder(), b, 0, len).toString();
    }

    public static StringBuilder dumpBytes(StringBuilder sb, byte[] b, int off, int len) {
        for (int i = off, j = 1; i < len; i++, j++) {
            sb.append(hexChar((b[i] >>> 4) & 0xf))
                    .append(hexChar(b[i] & 0xf));
            if ((j & 1) == 0)
                sb.append(' ');

            if ((j & 7) == 0) {
                sb.append('\n');
            }
        }

        return sb;
    }

    public static char hexChar(int a) {
        return (char) (a < 10 ? 48 + a : (a < 16 ? 55 + a : '!'));
    }

    public static int getNumber(char c) {
        if (c < 0x30 || c > 0x39) {
            return -1;
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

    public static Boolean isBoolean(String s) {
        return "true".equals(s) ? Boolean.TRUE : ("false".equals(s) ? Boolean.FALSE : null);
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
        StringBuilder sb = new StringBuilder();
        for (String s : args) {
            sb.append(s).append(splitChar);
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    public static String concat(String[] args, CharSequence splitSequence) {
        if (args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : args) {
            sb.append(s).append(splitSequence);
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
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

    public static List<String> split(List<String> dest, CharSequence str, char delim) {
        return split(dest, new CharList(), str, delim, Integer.MAX_VALUE, false);
    }

    public static List<String> split(List<String> dest, CharSequence str, char delim, int max) {
        return split(dest, new CharList(), str, delim, max, false);
    }

    public static List<String> split(List<String> dest, CharList tmp, CharSequence str, char delim) {
        return split(dest, tmp, str, delim, Integer.MAX_VALUE, false);
    }

    public static List<String> split(List<String> dest, CharList tmp, CharSequence str, char delim, int max) {
        return split(dest, tmp, str, delim, max, false);
    }

    public static List<String> split(List<String> dest, CharList tmp, CharSequence str, char c, int max, boolean keepEmpty) {
        tmp.clear();

        for (int i = 0; i < str.length(); i++) {
            char c1 = str.charAt(i);
            if (c == c1) {
                if (tmp.length() > 0 || keepEmpty) {
                    if (--max == 0) {
                        tmp.append(str, i, str.length() - i);
                        dest.add(tmp.toString());
                        tmp.clear();
                        return dest;
                    } else {
                        dest.add(tmp.toString());
                        tmp.clear();
                    }
                }
            } else {
                tmp.append(c1);
            }
        }

        if (tmp.length() > 0) {
            dest.add(tmp.toString());
            tmp.clear();
        }

        return dest;
    }

    public static List<String> splitPlus(List<String> dest, CharList tmp, CharSequence str, CharSequence delim) {
        return splitPlus(dest, tmp, str, delim, Integer.MAX_VALUE, false);
    }

    public static List<String> splitPlus(List<String> dest, CharList tmp, CharSequence str, CharSequence find, int max, boolean keepEmpty) {
        tmp.clear();
        if(find.length() == 0) {
            for (int i = 0; i < str.length(); i++) {
                dest.add(String.valueOf(str.charAt(i)));
            }
            return dest;
        }

        char first = find.charAt(0);

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (first == c && lastMatches(str, i, find, 0, find.length()) == find.length()) {
                if (tmp.length() > 0 || keepEmpty) {
                    if (--max == 0) {
                        tmp.append(str, i, str.length() - i);
                        dest.add(tmp.toString());
                        tmp.clear();
                        return dest;
                    } else {
                        dest.add(tmp.toString());
                        tmp.clear();
                    }
                }
            } else {
                tmp.append(c);
            }
        }

        if (tmp.length() > 0) {
            dest.add(tmp.toString());
            tmp.clear();
        }

        return dest;
    }
}
