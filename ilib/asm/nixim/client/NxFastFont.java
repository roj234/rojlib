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
package ilib.asm.nixim.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.Int2IntMap;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast Font Renderer
 *
 * @author Roj234
 * @since  2022/4/6 21:40
 */
@Nixim(value = "net.minecraft.client.gui.FontRenderer")
class NxFastFont extends FontRenderer {
    @Copy
    private static final String MAGIC = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./012345" +
        "6789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000Çüéâäà" +
        "åçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄" +
        "▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";
    @Copy(targetIsFinal = true, staticInitializer = "init1")
    private static Int2IntMap ID_MAP;

    private static void init1() {
        Int2IntMap idMap = ID_MAP = new Int2IntMap(MAGIC.length());
        String s = MAGIC;
        for (int i = 0; i < s.length(); i++) {
            idMap.putInt(s.charAt(i), i);
        }
    }

    @Shadow("field_78293_l")
    boolean unicodeFlag;

    public NxFastFont(GameSettings a, ResourceLocation b, TextureManager c, boolean d) {
        super(a, b, c, d);
    }

    @Inject("func_78263_a")
    public int getCharWidth(char c) {
        if (c == 160) {
            return 4;
        } else if (c == 167) {
            return -1;
        } else if (c == ' ') {
            return 4;
        } else {
            if (c > 0 && !unicodeFlag && ID_MAP.containsKey(c)) {
                return this.charWidth[ID_MAP.getOrDefault(c, 0)];
            } else if (this.glyphWidth[c] != 0) {
                int j = this.glyphWidth[c] & 255;
                return ((j & 15) + 1 - (j >>> 4)) / 2 + 1;
            } else {
                return 0;
            }
        }
    }

    @Inject("func_78271_c")
    public List<String> listFormattedStringToWidth(String str, int wrapWidth) {
        List<String> list = new ArrayList<>();
        wrapFormattedStringToWidth__IL(str, wrapWidth, list);
        return list;
    }

    @Copy
    void wrapFormattedStringToWidth__IL(String str, int wrapWidth, List<String> list) {
        while (true) {
            int i = this.sizeStringToWidth(str, wrapWidth);
            if (str.length() <= i) {
                list.add(str);
                break;
            } else {
                String s = str.substring(0, i);
                list.add(s);
                char delim = str.charAt(i);
                boolean isLinkBreak = delim == ' ' || delim == '\n';
                str = getFormatFromString(s) + str.substring(i + (isLinkBreak ? 1 : 0));
            }
        }
    }

    @Inject("func_78280_d")
    String wrapFormattedStringToWidth(String str, int wrapWidth) {
        StringBuilder sb = new StringBuilder();

        while (true) {
            int i = this.sizeStringToWidth(str, wrapWidth);
            if (str.length() <= i) {
                return sb.append(str).toString();
            } else {
                sb.append(str, 0, i);
                char delim = str.charAt(i);
                boolean isLinkBreak = delim == ' ' || delim == '\n';
                str = getFormatFromStringInternal(str, 0, i).append(str, i + (isLinkBreak ? 1 : 0), str.length()).toString();
            }
        }
    }

    @Shadow("func_78259_e")
    private int sizeStringToWidth(String str, int wrapWidth) {
        throw new NoSuchMethodError();
    }

    @Shadow("func_78272_b")
    private static boolean isFormatColor(char colorChar) {
        throw new NoSuchMethodError();
    }

    @Shadow("func_78270_c")
    private static boolean isFormatSpecial(char formatChar) {
        throw new NoSuchMethodError();
    }

    @Copy
    public static StringBuilder getFormatFromStringInternal(CharSequence text, int i, int end) {
        StringBuilder s = new StringBuilder();
        end--;

        while((i = TextUtil.limitedIndexOf(text, '\u00a7', i, 32767)) != -1) {
            if (i < end) {
                char c0 = text.charAt(i + 1);
                if (isFormatColor(c0)) {
                    s = new StringBuilder().append("§").append(c0);
                } else if (isFormatSpecial(c0)) {
                    s.append("§").append(c0);
                }
            } else {
                break;
            }
        }

        return s;
    }
}
