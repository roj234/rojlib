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

import roj.collect.Int2IntBiMap;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.TrieTree;
import roj.io.IOUtil;
import roj.management.SystemInfo;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/30 20:51
 */
public class JPinyin {
    public static final boolean DEBUG = false;

    public static void main(String[] args) throws IOException {
        //System.out.println(SystemInfo.getMemoryUsage());
        long t = System.currentTimeMillis();
        long mem = SystemInfo.getMemoryUsed();
        JPinyin pinyin = new JPinyin(IOUtil.readUTF(new FileInputStream(args[0])), IOUtil.readUTF(new FileInputStream(args[1])), IOUtil.readUTF(new FileInputStream(args[2])), IOUtil.readUTF(new FileInputStream(args[3])), 1);
        System.out.println("加载时间: " + (System.currentTimeMillis() - t) + "ms");
        System.out.println("占用内存: " + ((SystemInfo.getMemoryUsed() - mem) >> 10) + "KB");

        System.out.print("拼音： ");
        System.out.println(pinyin.toPinyin(args[4]));
        System.out.print("拼音忽略： ");
        System.out.println(pinyin.toPinyin(args[4], new StringBuilder(), 8));
        System.out.print("简转繁： ");
        System.out.println(args[4] = pinyin.toTraditionalChinese(args[4]));
        System.out.print("繁转简： ");
        System.out.println(pinyin.toSimplifiedChinese(args[4]));

        //System.out.println(SystemInfo.getMemoryUsage());
    }

    protected final IntMap<String> charYin;
    protected final TrieTree<String> wordYin, wordS2T, wordT2S;
    protected final Int2IntBiMap T2S;

    /**
     * @param pinyinFile 简繁汉字拼音文件
     * @param wordS2T    简转繁词表
     * @param wordT2S    繁转简词表
     * @param wordYin    词语特殊读音表
     * @param flag       1标上声调 0标上数字 -1去掉声调
     */
    public JPinyin(String pinyinFile, @Nullable String wordS2T, @Nullable String wordT2S, @Nullable String wordYin, int flag) {
        this.wordYin = wordYin == null ? null : new TrieTree<>();
        this.wordS2T = wordS2T == null ? null : new TrieTree<>();
        this.wordT2S = wordT2S == null ? null : new TrieTree<>();
        this.charYin = new IntMap<>();
        this.T2S = wordS2T != null || wordT2S != null ? new Int2IntBiMap() : null;

        parse(pinyinFile, wordS2T, wordT2S, wordYin, (byte) flag);
    }

    private void parse(String pinyin, String wordS2T, String wordT2S, String wordYin, byte flag) {
        CharList cl = new CharList(32);

        List<String> list0 = TextUtil.split(new ArrayList<>(10000), cl, pinyin, '\n');
        List<String> list1 = new ArrayList<>(5);
        List<String> list2 = new ArrayList<>(10);

        IntList notYin = DEBUG ? new IntList() : null;

        for (String s : list0) {
            if (s.startsWith("#")) continue;
            TextUtil.split(list1, cl, s, '=');

            final int cp = list1.get(0).codePointAt(0);

            if (list1.size() < 2) {
                if (DEBUG)
                    notYin.add(cp);
                charYin.put(cp, "[?Y]");
            } else {
                TextUtil.split(list2, cl, list1.get(1), ',');
                //for(String s3 : list2) {
                String s3 = list2.get(0);
                switch (flag) {
                    case -1:
                        s3 = s3.substring(0, s3.length() - 1);
                        break;
                    case 1:
                        s3 = toUnicode(s3);
                        break;
                }
                charYin.put(cp, s3);
                //}
                list2.clear();

                if (list1.size() > 2 && (wordS2T != null || wordT2S != null)) {
                    String TridChar = list1.get(2);
                    T2S.put(TridChar.codePointAt(0), cp);
                }
            }
            list1.clear();
        }

        if (DEBUG && !notYin.isEmpty()) {
            StringBuilder sb = new StringBuilder((notYin.size() * 3) >> 1).append("没有注音的字: ");
            for (PrimitiveIterator.OfInt itr = notYin.iterator(); itr.hasNext(); ) {
                sb.appendCodePoint(itr.nextInt()).append(',');
            }
            System.err.println(sb.deleteCharAt(sb.length() - 1));
        }

        if (wordT2S != null) {
            list0.clear();
            TextUtil.split(list0, cl, wordT2S, '\n');

            for (String s : list0) {
                if (s.startsWith("#")) continue;
                TextUtil.split(list1, cl, s, '=');

                String from = list1.get(0);
                String to = list1.get(1);
                this.wordT2S.put(from, to);

                list1.clear();
            }
        }

        if (wordS2T != null) {
            list0.clear();
            TextUtil.split(list0, cl, wordS2T, '\n');

            for (String s : list0) {
                if (s.startsWith("#")) continue;
                TextUtil.split(list1, cl, s, '=');

                String from = list1.get(0);
                String to = list1.get(1);
                this.wordS2T.put(from, to);

                list1.clear();
            }
        }

        if (wordYin != null) {
            list0.clear();
            TextUtil.split(list0, cl, wordYin, '\n');

            for (String s : list0) {
                if (s.startsWith("#")) continue;
                TextUtil.split(list1, cl, s, '=');

                String from = list1.get(0);
                String to = list1.get(1);

                switch (flag) {
                    case -1:
                        to = to.substring(0, to.length() - 1);
                        break;
                    case 1:
                        to = toUnicode(to);
                        break;
                }
                this.wordYin.put(from, to);

                list1.clear();
            }
        }
    }

    private static String toUnicode(String s) {
        boolean matches = true;
        int c = s.charAt(s.length() - 1);
        if (c < 0x30 || c > 0x39)
            matches = false;
        else {
            for (int i = 0, l = s.length() - 1; i < l; i++) {
                c = s.charAt(i);
                if (c < 97 || c > 122) {
                    matches = false;
                    break;
                }
            }
        }

        if (matches) {
            String vowel = null;

            int i = s.indexOf('a');
            if (i != -1) {
                vowel = "āáăàa";
            } else if ((i = s.indexOf('e')) != -1) {
                vowel = "ēéĕèe";
            } else if ((i = s.indexOf("ou")) != -1) {
                vowel = "ōóŏòo";
            } else {
                out:
                for (i = s.length() - 1; i >= 0; i--) {
                    switch (s.charAt(i)) {
                        case 'a':
                            vowel = "āáăàa";
                            break out;
                        case 'e':
                            vowel = "ēéĕèe";
                            break out;
                        case 'i':
                            vowel = "īíĭìi";
                            break out;
                        case 'o':
                            vowel = "ōóŏòo";
                            break out;
                        case 'u':
                            vowel = "ūúŭùu";
                            break out;
                        case 'v':
                            vowel = "ǖǘǚǜü";
                            break out;
                    }
                }
                if (i < 0) {
                    return s;
                }
            }

            char vowelChar = vowel.charAt(TextUtil.c2i(s.charAt(s.length() - 1)) - 1);

            return new CharList(s.length()).append(s, 0, i).append(vowelChar).append(s, i + 1, s.length() - 1).replace('v', 'ü').toString();
        } else {
            // only replace v with ü (umlat) character
            return s.replace('v', 'ü');
        }
    }

    public String toPinyin(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 3);

        operate(s, sb, 24);

        return sb.toString();
    }

    /**
     * @param flag 8 : ignore, 24 : keep, default: use [?]
     */
    public StringBuilder toPinyin(String s, StringBuilder sb, int flag) {
        operate(s, sb, flag & ~3);
        return sb;
    }

    protected final void operate(String src, StringBuilder dst, int type) {
        final int length = src.length();
        int i = 0;

        while (i < length) {
            char c1 = src.charAt(i);
            if (!Character.isHighSurrogate(c1)) {
                i = accept(src, dst, c1, i, type);
            } else {
                char c2 = src.charAt(i + 1);
                if (Character.isLowSurrogate(c2)) {
                    i = accept(src, dst, Character.toCodePoint(c1, c2), ++i, type);
                } else {
                    i = accept(src, dst, c1, i, type);
                }
            }
        }
    }

    protected final int accept(String src, StringBuilder dst, int cp, int off, int cat) {
        Map.Entry<Integer, String> specification;
        switch (cat & 7) {
            case 0: { // simplified to pinyin

                specification = this.wordYin.longestMatches(src, off, src.length());
                if (specification != null) {
                    dst.append(specification.getValue());
                    return off + specification.getKey();
                } else {
                    if ((cat & 8) != 0) {
                        String v = charYin.get(cp);
                        if (v != null)
                            dst.append(v);
                        else if ((cat & 16) != 0) {
                            dst.appendCodePoint(cp);
                        }
                    } else {
                        dst.append(charYin.getOrDefault(cp, "[?]"));
                    }
                }
            }
            break;
            case 1: { // simplified to traditional
                specification = this.wordS2T.longestMatches(src, off, src.length());
                if (specification != null) {
                    dst.append(specification.getValue());
                    return off + specification.getKey();
                } else {
                    dst.appendCodePoint(this.T2S.getByValueOrDefault(cp, cp));
                }
            }
            break;
            case 2: { // traditional to simplified
                specification = this.wordT2S.longestMatches(src, off, src.length());
                if (specification != null) {
                    dst.append(specification.getValue());
                    return off + specification.getKey();
                } else {
                    dst.appendCodePoint(this.T2S.getOrDefault(cp, cp));
                }
            }
            break;
        }
        return off + 1;
    }

    public String toTraditionalChinese(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        operate(s, sb, 1);

        return sb.toString();
    }

    public void toTraditionalChinese(String s, StringBuilder sb) {
        operate(s, sb, 1);
    }

    public String toSimplifiedChinese(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        operate(s, sb, 2);

        return sb.toString();
    }

    public void toSimplifiedChinese(String s, StringBuilder sb) {
        operate(s, sb, 2);
    }
}
