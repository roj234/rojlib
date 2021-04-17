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

package ilib.util;

import roj.util.Color;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public enum Colors {
/*
  OBFUSCATED("OBFUSCATED", 'k', true),
  BOLD("BOLD", 'l', true),
  STRIKETHROUGH("STRIKETHROUGH", 'm', true),
  UNDERLINE("UNDERLINE", 'n', true),
  ITALIC("ITALIC", 'o', true),
  RESET("RESET", 'r', -1);*/

    BLACK('0', 0, 0, 0, 100),
    RED('c', 255, 0, 0, 100), //
    DARK_GREEN('2', 0, 170, 0, 100),  //
    DARK_BLUE('1', 0, 0, 170, 100),  //
    DARK_PURPLE('5', 170, 0, 170, 100),//
    DARK_AQUA('3', 0, 255, 100),//
    GREY('7', 170, 170, 170, 100),
    DARK_GREY('8', 85, 85, 85),
    DARK_RED('4', 170, 0, 0, 100),//
    AQUA('b', 85, 255, 255), //
    GREEN('a', 85, 255, 85), //
    YELLOW('e', 255, 255, 85), //
    BLUE('9', 85, 85, 255),//
    LIGHT_PURPLE('d', 255, 85, 255), //
    ORANGE('6', 255, 170, 0, 100),//
    WHITE('f', 255, 255, 255);

    public final String code;
    public final Color color;

    Colors(char s, int... rgba) {
        code = new String(new char[]{'\u00a7', s});
        color = new Color(rgba[0], rgba[1], rgba[2], rgba.length > 3 ? rgba[3] : 150);
    }

    public String coloredName() {
        return code + name().toLowerCase();
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String toString() {
        return code;
    }
}