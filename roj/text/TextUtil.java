package roj.text;

import roj.collect.IBitSet;
import roj.collect.IntMap;
import roj.collect.LongBitSet;
import roj.config.ParseException;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.math.MathUtils;
import roj.util.log.LogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TextUtil.java
 */
public class TextUtil {
    @Deprecated
    private static final IntMap<Map<String, String>> langs = new IntMap<>();

    @Deprecated
    public static String translate(int type, String key) {
        Map<String, String> map = langs.get(type);
        if (map == null) {
            LogManager.getLogger("TextUtil").warn("Language id " + type + " was not found...");
            return key;
        }
        String result = map.get(key);
        if (result == null) return key;
        return result;
    }

    @Deprecated
    public static void loadLang(int type, String string) {
        langs.put(type, loadLang(string));
    }

    @Deprecated
    public static Map<String, String> loadLang(String content) {
        Map<String, String> map = new HashMap<>();
        //map.put("status", "err");
        if (content == null) return map;
        try {
            //String content = FileUtil.fileToString(file);
            String[] entries = content.replace("\n", "/;/").replace("\r", "/;/").replace("/;//;/", "/;/").split("/;/");
            boolean ignoreline = false;
            StringBuilder sb = null;
            for (String entry : entries) {
                String[] k_v = null;
                if (!ignoreline) {
                    k_v = entry.split("=", 2);
                    if (k_v[1].startsWith("#strl")) {
                        ignoreline = true;
                        sb = new StringBuilder(k_v[1].substring(5));
                    } else {
                        map.put(k_v[0], k_v[1]);
                    }
                } else {
                    if (entry.equals("#endl")) {
                        ignoreline = false;
                        map.put(k_v[0], sb.toString());
                        sb = null;
                    }
                }
            }
            //map.put("status", "ok");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    public static CharList repeat(int num, char ch) {
        if(num <= 0) return new CharList();
        CharList sb = new CharList(num);
        for (int i = 0; i < num; i++) {
            sb.append(ch);
        }
        return sb;
    }

    public static String scaledDouble(double d) {
        StringBuilder sb = new StringBuilder(Double.toString(d));
        if (sb.length() > 5) {
            sb.delete(5, sb.length());
        }
        return sb.toString();
    }

    public final static byte[] digits = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    public static final IBitSet ASCII_CHARACTERS = LongBitSet.preFilled(digits);

    /*public static CharList numberToString(int i, int radix) {
        if (radix < 2 || radix > 10 + 26 + 26)
            radix = 10;

        CharList buf = new CharList(32 - radix);
        boolean negative = (i < 0);
        int charPos = 32;

        if (!negative) {
            i = -i;
        }

        while (i <= -radix) {
            buf[charPos--] = digits[-(i % radix)];
            i = i / radix;
        }
        buf[charPos] = digits[-i];

        if (negative) {
            buf[--charPos] = '-';
        }

        return ;//new Cha(buf, charPos, (33 - charPos));
    }*/

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

    public static StringBuilder unescape(final CharSequence src) {
        int len;
        StringBuilder tmp = new StringBuilder(len = src.length());
        int i = 0, pos;

        //ByteList b8buf = new ByteList(2);
        //CharList u8buf = new CharList(2);

        while (i < len) {
            pos = limitedIndexOf(src, '%', i, len);
            if (pos == i) {
                if (src.charAt(pos + 1) == 'u') {
                    int ch = MathUtils.parseInt(false, src.subSequence(pos + 2, pos + 6), 16);

                    /*b8buf.pointer = 2;
                    b8buf.set(0, (byte) ch);
                    b8buf.set(1, (byte) (ch >> 8));

                    try {
                        ByteReader.decodeUTF(2, u8buf, b8buf);
                    } catch (UTFDataFormatException e) {
                        throw new RuntimeException(e);
                    }
                    tmp.append(u8buf.charAt(0));
                    u8buf.clear();*/
                    tmp.append((char) ch);

                    i = pos + 6;
                } else {
                    char ch = (char) MathUtils.parseInt(false, src.subSequence(pos + 1, pos + 3), 16);
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

    /**
     * @implNote 忽略空字符
     */
    public static String[] splitString(String keys, char c) {
        List<String> list = new ArrayList<>();
        return splitStringF(list, keys, c).toArray(new String[list.size()]);
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

    /**
     * @implNote 忽略空字符
     */
    public static String[] splitString(CharSequence keys, char c, int max) {
        List<String> list = new ArrayList<>();
        CharList chars = new CharList();
        for (int i = 0; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            if (c == c1) {
                if (chars.length() > 0) {
                    list.add(chars.toString());
                    chars.clear();
                    if (--max == 0) return list.toArray(new String[list.size()]);
                }
            } else {
                chars.append(c1);
            }
        }

        if (chars.length() > 0) {
            list.add(chars.toString());
            chars.clear();
        }

        return list.toArray(new String[list.size()]);
    }

    public static void replaceVariable(Map<String, String> env, String tag, List<String> list) {
        try {
            Lexer lexer = new Lexer().init(tag);
            while (lexer.hasNext()) {
                Word word = lexer.readStringToken();
                String val = word.val();
                if(word.type() == WordPresets.INTEGER) {
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

    public static int limitedLastIndexOf(CharSequence val, char ch, int off, int max) {
        for (int i = val.length() - 1 - max, low = Math.max(0, i - max); i >= low; i--) {
            if (val.charAt(i) == ch)
                return i;
        }
        return -1;
    }

    public static int limitedLastIndexOf(CharSequence val, char ch, int max) {
        return limitedLastIndexOf(val, ch, 0, max);
    }

    private static int sizeFor(CharSequence s) {
        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            k += (c > 255) ? 2 : 1;
        }
        return k;
    }

    public static String clear(CharSequence s) {
        return clear(sizeFor(s));
    }

    private static String clear(int length) {
        return repeat(length, '\b').toString();
    }

    public static List<String> splitStringF(List<String> list, CharSequence keys, char c) {
        return splitStringF(list, new CharList(), keys, c, Integer.MAX_VALUE, false);
    }

    public static List<String> splitStringF(List<String> list, CharList chars, CharSequence keys, char c) {
        return splitStringF(list, chars, keys, c, Integer.MAX_VALUE, false);
    }

    public static List<String> splitStringF(List<String> list, CharList chars, CharSequence keys, char c, int max) {
        return splitStringF(list, chars, keys, c, max, false);
    }

    public static List<String> splitStringF(List<String> list, CharList chars, CharSequence keys, char c, int max, boolean keepEmpty) {
        chars.clear();

        for (int i = 0; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            if (c == c1) {
                if (chars.length() > 0 || keepEmpty) {
                    if (--max == 0) {
                        chars.append(keys, i, keys.length() - i);
                        list.add(chars.toString());
                        chars.clear();
                        return list;
                    } else {
                        list.add(chars.toString());
                        chars.clear();
                    }
                }
            } else {
                chars.append(c1);
            }
        }

        if (chars.length() > 0) {
            list.add(chars.toString());
            chars.clear();
        }

        return list;
    }

    public static List<String> splitCStringF(List<String> list, CharList chars, CharSequence keys, CharSequence c) {
        return splitCStringF(list, chars, keys, c, Integer.MAX_VALUE, false);
    }

    public static List<String> splitCStringF(List<String> list, CharList chars, CharSequence keys, CharSequence find, int max, boolean keepEmpty) {
        chars.clear();
        if(find.length() == 0) {
            for (int i = 0; i < keys.length(); i++) {
                chars.append(keys.charAt(i));
                list.add(chars.toString());
                chars.clear();
            }
            return list;
        }

        char kw = find.charAt(0);

        for (int i = 0; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            if (kw == c1 && lastMatches(keys, i, find, 0, find.length()) == find.length()) {
                if (chars.length() > 0 || keepEmpty) {
                    if (--max == 0) {
                        chars.append(keys, i, keys.length() - i);
                        list.add(chars.toString());
                        chars.clear();
                        return list;
                    } else {
                        list.add(chars.toString());
                        chars.clear();
                    }
                }
            } else {
                chars.append(c1);
            }
        }

        if (chars.length() > 0) {
            list.add(chars.toString());
            chars.clear();
        }

        return list;
    }
}
