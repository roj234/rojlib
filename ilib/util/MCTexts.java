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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: UsefulFunctions.java
 */
package ilib.util;

import org.lwjgl.input.Keyboard;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.JPinyin;
import roj.text.TextUtil;
import roj.util.EmptyArrays;

import net.minecraft.util.text.translation.I18n;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MCTexts {
    private static JPinyin pinyin;

    public static JPinyin pinyin() {
        if (pinyin == null) {
            try {
                pinyin = new JPinyin(IOUtil.readUTF("META-INF/pinyin/char_t2s_yin.txt"),
                                     IOUtil.readUTF("META-INF/pinyin/word_s2t.txt"),
                                     IOUtil.readUTF( "META-INF/pinyin/word_t2s.txt"),
                                     IOUtil.readUTF("META-INF/pinyin/word_yin.txt"),
                                     -1);
            } catch (IOException e) {
                e.printStackTrace();
                pinyin = new JPinyin("", null, null, null, 0);
            }
        }
        return pinyin;
    }

    public static String format(String text, Object... param) {
        text = I18n.translateToLocalFormatted(text, param);
        if (!text.contains("\\n")) return text;
        CharList tmp = IOUtil.getSharedCharBuf()
                             .append(text)
                             .replace("\\n", "\n");
        return tmp.toString();
    }

    public static String format(String text) {
        text = I18n.translateToLocal(text);
        if (!text.contains("\\n")) return text;
        CharList tmp = IOUtil.getSharedCharBuf()
                             .append(text)
                             .replace("\\n", "\n");
        return tmp.toString();
    }

    public static String format(boolean flag) {
        return format(flag ? "tooltip.true" : "tooltip.false", EmptyArrays.OBJECTS);
    }

    public static int getStringWidth(CharSequence s) {
        float width = 0;
        for (int i = 0; i < s.length(); i++) {
            width += getCharWidth(s.charAt(i));
        }
        return (int) Math.ceil(width);
    }

    public static List<String> splitByWidth(CharSequence s, int maxWidth) {
        List<String> list = new ArrayList<>();

        CharList sb = IOUtil.getSharedCharBuf();

        float width = 0;

        int state = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float charWidth = getCharWidth(c);

            if (state > 3) {
                state &= 3;
                switch (c) {
                    case 'l':
                    case 'L':
                        state |= 2;
                        break;
                    case 'r':
                    case 'R':
                        state = 0;
                        break;
                    case 'o':
                    case 'O':
                        state |= 1;
                        width += 2;
                        break;
                }
            } else if (c == '\u00a7') {
                state = 4;
            } else if (c == '\n') {
                width = maxWidth + 1;
            } else {
                width += charWidth;
                if ((state & 2) != 0) {
                    width += charWidth * 0.3f;
                }
            }

            if (width > maxWidth) {
                list.add(sb.toString());
                if (list.size() > 10000) return list;
                sb.clear();
                width = 0;
                state = 0;
                if (c != '\r' && c != '\n') i--;
            } else {
                sb.append(c);
            }
        }

        if (sb.length() > 0) list.add(sb.toString());

        return list;
    }

    public static float getCharWidth(char c) {
        switch (c) {
            case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7: case 8: case 11:
            case 12: case 14: case 15: case 16: case 17: case 18: case 19: case 20:
            case 21: case 22: case 23: case 24: case 25: case 26: case 27: case 28:
            case 29: case 30: case 31:
                return 9.1f;
            case '~': case '@':
                return 7.1f;
            case 'I': case 't':
            case '[': case ']':
            case ' ':
                return 4.1f;
            case '\'': case '`':
            case 'l':
                return 3.05f;
            case '"': case '*':
            case 'f': case 'k':
            case '>': case '<': case '{': case '}': case '(': case ')':
                return 5.1f;
            case '!': case '|': case '.': case ',': case ':':
            case 'i':
                return 2.1f;
            default:
                return c > 255 ? 8.5f : 6.1f;
        }
    }

    private static final char[] rainbowTooltip = new char[]{
            'c', '6', 'e', 'a', 'b', '9', 'd'};
    private static final char[] sonic          = new char[]{
            '9', '9', '9', '9', 'f', '9', 'f', 'f', '9', 'f',
            'f', '9', 'b', 'f', '7', '7', '7', '7', '7', '7',
            '7', '7', '7', '7', '7', '7', '7', '7', '7', '7'};

    public static String colorTooltip(char[] chars, String tip, long tick, float speed) {
        CharList sb = IOUtil.getSharedCharBuf();
        int index = (int) ((float) tick / speed) % chars.length;
        for (int i = 0; i < tip.length(); i++) {
            char c = tip.charAt(i);
            int subIndex = (index++) % chars.length;
            sb.append('\u00a7').append(chars[subIndex]).append(c);
        }
        return sb.append('\u00a7').append('r').toString();
    }

    public static String sonicTooltip(String tip) {
        return colorTooltip(sonic, tip, TimeUtil.tick, 1.5f);
    }

    public static String rainbowTooltip(String tip) {
        return colorTooltip(rainbowTooltip, tip, TimeUtil.tick, 1f);
    }

    @Deprecated
    public static void shiftLore(List<String> lores, String shifts) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            TextUtil.split(lores, shifts, '*');
        } else
            lores.add(format("tooltip.ilib.shift"));
    }

    public static String ticksToTime(int ticks) {
        CharList cl = IOUtil.getSharedCharBuf();
        if (ticks > 72000) {
            int h = ticks / 20 / 3600;
            cl.append(h).append(":");
        }

        ticks %= 72000;
        if (ticks > 1200) {
            int m = ticks / 20 / 60;
            if (m < 10) cl.append("0");
            cl.append(m).append(":");
        }

        ticks %= 1200;
        int s = ticks / 20, ms = ticks % 20 / 2;
        if (s < 10) cl.append("0");

        return cl.append(s).append(".").append(ms).toString();
    }
}
