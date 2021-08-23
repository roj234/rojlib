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
import ilib.asm.util.MCHooks;
import ilib.client.util.RenderUtils;
import org.lwjgl.opengl.GL11;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.RemapTo;
import roj.asm.nixim.Shadow;
import roj.collect.IntList;
import roj.text.TextUtil;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/3 19:40
 */
@Nixim(value = "net.minecraft.client.gui.FontRenderer", copyItf = true)
public class NiximFontRenderer extends FontRenderer implements IFontRenderer {

    @Copy
    int lastTexture;

    @Shadow("field_78285_g")
    int[] colorCode;
    @Shadow("field_78304_r")
    private int textColor;
    @Shadow("field_78300_v")
    private boolean underlineStyle;
    @Shadow("field_78299_w")
    private boolean strikethroughStyle;

    @Shadow("field_78303_s")
    private boolean randomStyle;
    @Shadow("field_78302_t")
    private boolean boldStyle;
    @Shadow("field_78301_u")
    private boolean italicStyle;

    @Shadow("field_78293_l")
    boolean unicodeFlag;

    @Shadow("field_78291_n")
    float red;
    @Shadow("field_78306_p")
    float green;
    @Shadow("field_78306_p")
    float blue;
    @Shadow("field_78305_q")
    float alpha;

    public NiximFontRenderer(GameSettings gameSettingsIn, ResourceLocation location, TextureManager textureManagerIn, boolean unicode) {
        super(gameSettingsIn, location, textureManagerIn, unicode);
    }

    /*@RemapTo()
    private void resetStyles() {
        this.strikethroughStyle = false;
        this.underlineStyle = false;
    }*/

    @RemapTo("func_78255_a")
    private void renderStringAtPos(String text, boolean shadow) {
        final int len = text.length();
        int flag = 0;

        int lastFlag;
        float strikePos = 0, lastX = posX;

        lastTexture = 0x80000000;

        for(int i = 0; i < len; ++i) {
            lastFlag = flag & 6;
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < len) {
                if(MCHooks.COLOR_CODE.contains(c = Character.toLowerCase(text.charAt(i + 1)))) {
                    if (MCHooks.HEX_CHAR.contains(c)) {
                        flag = 0;
                        int index = TextUtil.getNumber(c);
                        if(index == -1)
                            index = c - 'a' + 10;

                        if (shadow) {
                            index += 16;
                        }

                        int color = this.colorCode[index];
                        this.textColor = color;
                        this.setColor((float) (color >> 16) / 255, (float) (color >> 8 & 255) / 255, (float) (color & 255) / 255, this.alpha);
                    } else {
                        switch (c) {
                            case 'r': {
                                flag = 0;
                                /*this.boldStyle = false;
                                this.strikethroughStyle = false;
                                this.underlineStyle = false;
                                this.italicStyle = false;
                                this.randomStyle = false;*/
                                this.setColor(this.red, this.blue, this.green, this.alpha);
                            }
                            break;
                            case 'l':
                                //this.boldStyle = true;
                                flag |= 1;
                                break;
                            case 'm':
                                //this.strikethroughStyle = true;
                                flag |= 2;
                                break;
                            case 'n':
                                //this.underlineStyle = true;
                                flag |= 4;
                                break;
                            case 'o':
                                //this.italicStyle = true;
                                flag |= 8;
                                break;
                            case 'k':
                                //this.randomStyle = true;
                                flag |= 16;
                                break;
                        }
                    }
                    ++i;
                    continue;
                }
            }

            int index = MCHooks.AWFUL_ASCII_ID.getOrDefault(c, -1);
            if (index != -1 && (flag & 16) != 0) {
                int w = this.getCharWidth(c);

                IntList list = MCHooks.getOrCreateWidthTable(this, w);
                c = (char) list.get(this.fontRandom.nextInt(list.size()));
            }

            float offset = index != -1 && !this.unicodeFlag ? 1 : 0.5F;

            shadow &= (c == 0 || index == -1 || this.unicodeFlag);
            if (shadow) {
                this.posX -= offset;
                this.posY -= offset;
            }

            float xLen = this.renderChar(c, (flag & 8) != 0); // italic

            if (shadow) {
                this.posX += offset;
                this.posY += offset;
            }

            if ((flag & 1) != 0) { // bold
                if (shadow) {
                    this.posY -= offset;
                } else {
                    this.posX += offset;
                }

                this.renderChar(c, (flag & 8) != 0);

                if (shadow) {
                    this.posY += offset;
                } else {
                    this.posX -= offset;
                }

                ++xLen;
            }

            if(lastFlag != (flag & 6)) { // flag does changed
                if(lastFlag != 0) {
                    // flush and prepare
                    this.strikethroughStyle = (lastFlag & 2) != 0;
                    this.underlineStyle = (lastFlag & 4) != 0;

                    float backX = this.posX;
                    this.posX = lastX;
                    this.doDraw(strikePos);
                    this.posX = lastX = backX;
                } else {
                    // prepare
                    lastX = posX;
                }
                strikePos = xLen;
            } else if (lastFlag != 0) {
                // record
                strikePos += xLen;
            }
            // normal
            this.posX += (int)xLen;
        }

        lastTexture = 0;
        this.boldStyle = (flag & 1) != 0;
        this.strikethroughStyle = (flag & 2) != 0;
        this.underlineStyle = (flag & 4) != 0;
        this.italicStyle = (flag & 8) != 0;
        this.randomStyle = (flag & 16) != 0;
    }

    @RemapTo("func_78255_a")
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

    @RemapTo("func_78263_a")
    public int getCharWidth(char c) {
        switch (c) {
            case 160:
            case ' ':
                return 4;
            case 167:
                return -1;
        }
        int i;
        if (c > 0 && (i = MCHooks.AWFUL_ASCII_ID.getOrDefault(c, -1)) != -1 && !this.unicodeFlag) {
            return this.charWidth[i];
        } else if (this.glyphWidth[c] != 0) {
            int j = this.glyphWidth[c];
            int k = j >>> 4;
            int l = (j & 15) + 1;
            return (l - k) / 2 + 1;
        }
        return 0;
    }

    @RemapTo("func_181559_a")
    private float renderChar(char ch, boolean italic) {
        if((lastTexture & 0x80000000) == 0) {
            lastTexture = 0;
        }

        switch (ch) {
            case 160:
            case ' ':
                return 4;
        }
        int i = MCHooks.AWFUL_ASCII_ID.getOrDefault(ch, -1);
        return i != -1 && !this.unicodeFlag ? this.renderDefaultChar(i, italic) : this.renderUnicodeChar(ch, italic);
    }

    @RemapTo("func_78266_a")
    protected float renderDefaultChar(int ch, boolean italic) {
        float U = (ch & 15) << 3;
        float V = ((ch >>> 4) & 15) << 3;
        int k = italic ? 1 : 0;

        if((lastTexture & 0x7FFFFFFF) != 0x7FFFFFFF) {
            this.bindTexture(this.locationFontTexture);
            if((lastTexture & 0x80000000) != 0)
                lastTexture = -1;
        }

        int w = this.charWidth[ch];
        float off = w - 0.01F;

        BufferBuilder builder = RenderUtils.BUILDER;
        builder.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX);
        final float posX = this.posX, posY = this.posY;
        builder.pos(posX + k, posY, 0).tex(U / 128F, V / 128F).endVertex();
        builder.pos(posX - k, posY + 7.99F, 0).tex(U / 128F, (V + 7.99F) / 128F).endVertex();
        builder.pos(posX + off - 1 + k, posY, 0).tex((U + off - 1) / 128, V / 128F).endVertex();
        builder.pos(posX + off - 1 - k, posY + 7.99F, 0).tex((U + off - 1) / 128F, (V + 7.99F) / 128).endVertex();
        RenderUtils.TESSELLATOR.draw();

        return w;
    }

    @Shadow("func_78257_a")
    private void loadGlyphTexture(int page) {
    }

    @RemapTo("func_78277_a")
    protected float renderUnicodeChar(char ch, boolean italic) {
        int w = this.glyphWidth[ch] & 255;
        if (w == 0) {
            return 0;
        } else {

            if((lastTexture & 0x7FFFFFFF) != (ch >>> 8)) {
                this.loadGlyphTexture(ch >>> 8);
                if((lastTexture & 0x80000000) != 0)
                    lastTexture = 0x80000000 | ch >>> 8;
            }

            float U = (ch & 15) << 4; // ch % 16 * 16;
            float V = ((ch & 255) >>> 4) << 4; // ch & 255 / 16 * 16
            int f3 = (w & 15) + 1 - (w >>> 4);
            float f4 = f3 - 0.02F;
            float f5 = italic ? 1 : 0;

            BufferBuilder builder = RenderUtils.BUILDER;
            builder.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX);
            final float posX = this.posX, posY = this.posY;
            builder.pos(posX + f5, posY, 0).tex(U / 256F, V / 256F).endVertex();
            builder.pos(posX - f5, posY + 7.99F, 0).tex(U / 256F, (V + 15.98F) / 256F).endVertex();
            builder.pos(posX + f4 / 2F + f5, posY, 0).tex((U + f4) / 256F, V / 256F).endVertex();
            builder.pos(posX + f4 / 2F - f5, posY + 7.99F, 0).tex((U + f4) / 256F, (V + 15.98F) / 256F).endVertex();
            RenderUtils.TESSELLATOR.draw();
            return f3 / 2F + 1;
        }
    }

    @RemapTo("doDraw")
    protected void doDraw(float f) {
        BufferBuilder builder = RenderUtils.BUILDER;

        if(strikethroughStyle & underlineStyle) {
            GlStateManager.disableTexture2D();

            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

            final float posX = this.posX, posY = this.posY;
            if (strikethroughStyle) {
                // todo test builder 原先在这里, 分开了
                final float fhDiv2 = (this.FONT_HEIGHT >> 1);
                builder.pos(posX, posY + fhDiv2, 0).endVertex();
                builder.pos(posX + f, posY + fhDiv2, 0).endVertex();
                builder.pos(posX + f, posY + fhDiv2 - 1, 0).endVertex();
                builder.pos(posX, posY + fhDiv2 - 1, 0).endVertex();
            }

            if (underlineStyle) {
                final float fh = this.FONT_HEIGHT;
                builder.pos(posX - 1, posY + fh, 0).endVertex();
                builder.pos(posX + f, posY + fh, 0).endVertex();
                builder.pos(posX + f, posY + fh - 1, 0).endVertex();
                builder.pos(posX - 1, posY + fh - 1, 0).endVertex();
            }
            RenderUtils.TESSELLATOR.draw();

            GlStateManager.enableTexture2D();
        }

        this.posX += (int)f;
    }

    @RemapTo("func_78271_c")
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

    @RemapTo("func_78280_d")
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
        return 0;
    }

    @Shadow("func_78272_b")
    private static boolean isFormatColor(char colorChar) {
        return false;
    }

    @Shadow("func_78270_c")
    private static boolean isFormatSpecial(char formatChar) {
        return false;
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
    @Copy
    public int[] getColorCodeRawArray() {
        return colorCode;
    }

    @Override
    public Random getFontRandom() {
        return fontRandom;
    }

    @Override
    public int getAsciiWidth(int i) {
        return this.charWidth[i];
    }

    @Override
    public float renderAsciiChar(int ch, float posX, float posY, float k, boolean bind) {
        float U = (ch & 15) << 3;
        float V = ((ch >>> 4) & 15) << 3;

        if(bind)
            this.bindTexture(this.locationFontTexture);

        int w = this.charWidth[ch];
        float off = w - 0.01F;

        BufferBuilder builder = RenderUtils.BUILDER;
        builder.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX);
        builder.pos(posX + k, posY, 0).tex(U / 128F, V / 128F).endVertex();
        builder.pos(posX - k, posY + 7.99F, 0).tex(U / 128F, (V + 7.99F) / 128F).endVertex();
        builder.pos(posX + off - 1 + k, posY, 0).tex((U + off - 1) / 128, V / 128F).endVertex();
        builder.pos(posX + off - 1 - k, posY + 7.99F, 0).tex((U + off - 1) / 128F, (V + 7.99F) / 128).endVertex();
        RenderUtils.TESSELLATOR.draw();

        return w;
    }
}
