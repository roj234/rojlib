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

import ilib.ImpLib;
import org.lwjgl.input.Keyboard;
import roj.text.TextUtil;
import roj.util.EmptyArrays;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class TextHelperM {
    private static final char[] rainbowTooltip = new char[]{
            'c', '6', 'e', 'a', 'b', '9', 'd'
    };
    private static final char[] sanic = new char[]{
            '9', '9', '9', '9', 'f', '9', 'f', 'f', '9', 'f',
            'f', '9', 'b', 'f', '7', '7', '7', '7', '7', '7',
            '7', '7', '7', '7', '7', '7', '7', '7', '7', '7'};

    public static String translate(String text, Object... param) {
        if (ImpLib.isClient)
            return _trCli(text, param).replace("\\n", "\n");
        return text.replace(".name", "");
    }

    public static String translate(String text) {
        return translate(text, EmptyArrays.OBJECTS);
    }

    public static String translate(boolean flag) {
        return translate(flag ? "tooltip.true" : "tooltip.false", EmptyArrays.OBJECTS);
    }


    @SideOnly(Side.CLIENT)
    public static String translateMc(String key, Object... par) {
        return I18n.format(key, par);
    }

    @SideOnly(Side.CLIENT)
    private static String _trCli(String text, Object[] obj) {
        text = I18n.format(text);
        for (int i = 0; i < obj.length; i++) {
            text = text.replace("$" + i + "$s", String.valueOf(obj[i]));
        }
        return text;
    }

    public static List<String> splitString(final FontRenderer fr, final String string, final int width) {
        List<String> list = new ArrayList<>();

        StringBuilder sb = new StringBuilder();

        int sumWidth = 0;

        boolean isXJ = false;
        boolean isLarge = false;

        for (int l = 0; l < string.length(); l++) {
            char ch = string.charAt(l);
            int charWidth = fr.getCharWidth(ch);
            if (isXJ) {
                isXJ = false;
                if (ch != 'l' && ch != 'L') {
                    if (ch == 'r' || ch == 'R') {
                        isLarge = false;
                    }
                } else {
                    isLarge = true;
                }
            } else if (charWidth < 0) {
                isXJ = true;
            } else if (ch == '\n') {
                sumWidth = width;
            } else {
                sumWidth += charWidth;
                if (isLarge) {
                    ++sumWidth;
                }

                if (ch < 128 && !fr.getUnicodeFlag()) { // english char a-z
                    sumWidth += charWidth;
                }
            }

            if (sumWidth > width) {
                list.add(sb.toString());
                sb.delete(0, sb.length());
                sumWidth = 0;
                if (ch != '\r' && ch != '\n')
                    l--;
            } else {
                sb.append(ch);
            }
        }

        return list;
    }

    public static String getLangId() {
        return FMLCommonHandler.instance().getCurrentLanguage().toLowerCase();
    }

    protected static String colorTooltip(char[] chars, String tip, long tick, float speed) {
        StringBuilder sb = new StringBuilder((int) (tip.length() * 3.2f));
        int index = (int) ((float) tick / speed) % chars.length;
        for (int i = 0; i < tip.length(); i++) {
            char c = tip.charAt(i);
            int subIndex = (index++) % chars.length;
            sb.append('\u00a7').append(chars[subIndex]).append(c);
        }
        return sb.append('\u00a7').append('r').toString();
    }

    public static String sonicTooltip(String tip, long tick) {
        return colorTooltip(sanic, tip, tick, 1.5f);
    }

    public static String sonicTooltip(String tip) {
        return sonicTooltip(tip, TimeUtil.tick);
    }

    public static String rainbowTooltip(String tip, long tick) {
        return colorTooltip(rainbowTooltip, tip, tick, 1f);
    }

    public static String rainbowTooltip(String tip) {
        return rainbowTooltip(tip, TimeUtil.tick);
    }

    public static void shiftLore(List<String> lores, String shifts) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            TextUtil.split(lores, shifts, '*');
        } else
            lores.add(translate("tooltip.ilib.shift"));
    }
}
