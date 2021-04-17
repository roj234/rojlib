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

import ilib.asm.util.IFontRenderer;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.opengl.text.FontTex;
import roj.opengl.text.TextRenderer;
import roj.opengl.vertex.VertexBuilder;
import roj.text.TextUtil;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Roj234 Font Renderer
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/3 19:40
 */
@Nixim(value = "net.minecraft.client.gui.FontRenderer", copyItf = true)
public class NiximFontRenderer extends FontRenderer implements IFontRenderer {
    @Copy
    TextRenderer rojTextRender;

    @Shadow("field_78285_g")
    int[] colorCode;

    @Shadow("field_78293_l")
    boolean unicodeFlag;

    @Inject("<init>")
    public NiximFontRenderer(GameSettings g, ResourceLocation l, TextureManager tx, boolean u) {
        super(g, l, tx, u);
        setFont(new FontTex("黑体-20"));
    }

    @Inject("func_78255_a")
    private void renderStringAtPos(String text, boolean shadow) {
        posX = rojTextRender.i_render(text, shadow, posX, posY);
    }

    @Inject("func_78255_a")
    public int getStringWidth(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        } else {
            int i = 0, l = text.length();
            boolean Lflag = false;

            for(int j = 0; j < l; ++j) {
                char c = text.charAt(j);
                int w = this.getCharWidth(c);
                if (w < 0 && j < l - 1) {
                    c = text.charAt(++j);
                    if (c != 'l' && c != 'L') {
                        if (c == 'r' || c == 'R') {
                            Lflag = false;
                        }
                    } else {
                        Lflag = true;
                    }
                } else {
                    i += w;
                    if (Lflag && w > 0) {
                        ++i;
                    }
                }
            }

            return i;
        }
    }

    @Inject("func_78263_a")
    public int getCharWidth(char c) {
        return rojTextRender.getCharWidth(c);
    }

    @Inject("func_181559_a")
    private float renderChar(char ch, boolean italic) {
        throw new NoSuchMethodError();
    }

    @Inject("func_78266_a")
    protected float renderDefaultChar(int ch, boolean italic) {
        return rojTextRender.i_renderChar((char) ch, italic ? 1 : 0);
    }

    @Shadow("func_78257_a")
    private void loadGlyphTexture(int page) {
        throw new NoSuchMethodError();
    }

    @Inject("func_78277_a")
    protected float renderUnicodeChar(char ch, boolean italic) {
        return rojTextRender.i_renderChar(ch, italic ? 1 : 0);
    }

    @Inject("doDraw")
    protected void doDraw(float f) {
        throw new NoSuchMethodError();
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

    @Override
    public void setFont(FontTex unicode) {
        if(rojTextRender != null)
            rojTextRender.getVertexBuilder().free();
        rojTextRender = new TextRenderer(unicode, colorCode, new VertexBuilder(2048));
    }
}
