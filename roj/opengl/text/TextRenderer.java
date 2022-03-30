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
package roj.opengl.text;

import org.lwjgl.opengl.GL11;
import roj.collect.IntList;
import roj.collect.MyBitSet;
import roj.opengl.text.FontTex.Tex;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormats;
import roj.text.TextUtil;

import java.util.Random;

/**
 * Text Renderer
 *
 * @author Roj234
 * @since  2021/2/3 19:40
 */
public class TextRenderer {
    public static final int[]   COLOR_CODE;
    public static final Random   FONT_RND;
    public static final MyBitSet COLOR_CODE_TEXT = MyBitSet.from('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'k', 'l', 'm', 'n', 'o', 'r');
    public static final MyBitSet HEX_CHAR        = MyBitSet.from('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f');

    static {
        COLOR_CODE = new int[32];
        for(int i = 0; i < 32; ++i) {
            int j = (i >> 3 & 1) * 85;
            int k = (i >> 2 & 1) * 170 + j;
            int l = (i >> 1 & 1) * 170 + j;
            int i1 = (i >> 0 & 1) * 170 + j;
            if (i == 6) {
                k += 85;
            }

            if (i >= 16) {
                k /= 4;
                l /= 4;
                i1 /= 4;
            }

            COLOR_CODE[i] = (k & 255) << 16 | (l & 255) << 8 | i1 & 255;
        }
        FONT_RND = new Random();
    }

    public final FontTex font;
    private final VertexBuilder vb;
    private final int[] colorCode;

    public float scale = 1.0f;
    public int color;

    public int lastTexture, lineHeight;

    public TextRenderer(FontTex font, VertexBuilder vb) {
        this.font = font;
        this.vb = vb;
        this.colorCode = COLOR_CODE;
    }

    public TextRenderer(FontTex font, int[] colorCode, VertexBuilder vb) {
        this.font = font;
        this.vb = vb;
        this.colorCode = colorCode;
    }

    public VertexBuilder getVertexBuilder() {
        return vb;
    }

    public void renderString(String text, float posX, float posY) {
        if (scale != 1) {
            GL11.glPushMatrix();
            GL11.glScaled(scale, scale, scale);
            posX *= 1f / scale;
            posY *= 1f / scale;
            i_render(text, false, posX, posY);
            GL11.glPopMatrix();
        } else {
            i_render(text, false, posX, posY);
        }
    }

    public void renderStringWithShadow(String text, float posX, float posY) {
        GL11.glPushMatrix();
        if (scale != 1) {
            GL11.glScaled(scale, scale, scale);
            posX *= 1f / scale;
            posY *= 1f / scale;
        }
        i_render(text, true, posX + 0.5f, posY - 0.5f);
        GL11.glTranslatef(0, 0, 0.05f);
        i_render(text, false, posX, posY);
        GL11.glPopMatrix();
    }

    public final float i_render(String text, boolean shadow, float posX, float posY) {
        lastTexture = -1;
        lineHeight = font.preRender(text, 0, text.length());

        VertexBuilder vb = this.vb;
        vb.begin(VertexFormats.POSITION_TEX);

        int color = this.color;
        int flag = 0, lastFlag = 0;
        float lastX = 0;

        for(int i = 0; i < text.length(); ++i) {
            if(lastFlag != (flag & 6)) { // flag does changed
                if(lastFlag != 0) {
                    vb.end();
                    VboUtil.drawVertexes(GL11.GL_QUADS, vb);

                    // flush and prepare
                    this.i_drawLine(lastFlag, lastX, posY, posX - lastX);

                    vb.begin(VertexFormats.POSITION_TEX);
                }
                lastX = posX;
                lastFlag = flag & 6;
            }

            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                if(COLOR_CODE_TEXT.contains(c = Character.toLowerCase(text.charAt(i + 1)))) {
                    int tColor = color;
                    if (HEX_CHAR.contains(c)) {
                        flag = 0;
                        int index = TextUtil.c2i(c);
                        if(index == -1)
                            index = c - 'a' + 10;

                        if (shadow) {
                            index += 16;
                        }

                        tColor = colorCode[index];
                    } else {
                        switch (c) {
                            case 'r':
                                flag = 0;
                                tColor = 0xFFFFFF;
                            break;
                            case 'l':
                                flag |= 1;
                                break;
                            case 'm':
                                flag |= 2;
                                break;
                            case 'n':
                                flag |= 4;
                                break;
                            case 'o':
                                flag |= 8;
                                break;
                            case 'k':
                                flag |= 16;
                                break;
                        }
                    }
                    if(tColor != color) {
                        vb.end();
                        VboUtil.drawVertexes(GL11.GL_QUADS, vb);

                        if(lastFlag != 0) {
                            this.i_drawLine(lastFlag, lastX, posY, posX - lastX);
                            lastX = posX;
                        }

                        vb.begin(VertexFormats.POSITION_TEX);
                        Util.color(color = tColor);
                    }
                    ++i;
                    continue;
                }
            }

            if ((flag & 16) != 0) {
                int w = this.getCharWidth(c);

                IntList list = font.getEntriesByWidth(w);
                if(list.size() > 0)
                    c = (char) list.get(FONT_RND.nextInt(list.size()));
            }

            vb.translate(posX, posY, 0);
            float xLen = this.i_renderChar(c, (flag & 8) != 0 ? lineHeight / 8 : 0);

            if ((flag & 1) != 0) { // bold
                vb.translate(posX + (shadow ? 0.5f : 1f), posY, 0);

                this.i_renderChar(c, (flag & 8) != 0 ? lineHeight / 8 : 0);

                xLen++;
            }

            posX += xLen + 1;
        }

        vb.end();
        VboUtil.drawVertexes(GL11.GL_QUADS, vb);
        return posX;
    }

    public final int getCharWidth(char c) {
        switch (c) {
            case 160:
            case ' ':
                return 4;
            case 167:
                return -1;
        }
        return font.getOrCreateEntry(c).width;
    }

    public final float i_renderChar(char ch, float italic) {
        switch (ch) {
            case 160:
            case ' ':
                return lineHeight >> 2;
        }
        Tex tex = font.getOrCreateEntry(ch);
        if (tex == null) {
            return 0;
        } else {
            VertexBuilder vb = this.vb;
            if(lastTexture != tex.textureId) {
                VboUtil.drawVertexes(GL11.GL_QUADS, vb);
                Util.bindTexture(tex.textureId);
                vb.begin(VertexFormats.POSITION_TEX);
                lastTexture = tex.textureId;
            }

            int h = (lineHeight - tex.height - tex.bottom) / 2;
            vb.yOffset += h;
            float width = (float) tex.width / 2F;
            float height = (float) tex.height / 2F;
            vb.pos(italic, height, 0).tex(tex.u1, tex.v2).endVertex();
            vb.pos(italic + width, height, 0).tex(tex.u2, tex.v2).endVertex();
            vb.pos(-italic + width, 0, 0).tex(tex.u2, tex.v1).endVertex();
            vb.pos(-italic, 0, 0).tex(tex.u1, tex.v1).endVertex();
            vb.yOffset -= h;
            return width;
        }
    }

    public final void i_drawLine(int flag, float posX, float posY, float len) {
        VertexBuilder vb = this.vb;

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        vb.end();
        vb.begin(VertexFormats.POSITION);

        // 删除线
        if ((flag & 2) != 0) {
            float fhDiv2 = lineHeight / 4f;
            vb.pos(posX, posY + fhDiv2, 0).endVertex();
            vb.pos(posX + len, posY + fhDiv2, 0).endVertex();
            vb.pos(posX + len, posY + fhDiv2 - 1, 0).endVertex();
            vb.pos(posX, posY + fhDiv2 - 1, 0).endVertex();
        }

        // 下划线
        if ((flag & 4) != 0) {
            float fhDiv2 = lineHeight / 2f;
            vb.pos(posX - 1, posY + fhDiv2 , 0).endVertex();
            vb.pos(posX + len, posY + fhDiv2, 0).endVertex();
            vb.pos(posX + len, posY + fhDiv2 - 1, 0).endVertex();
            vb.pos(posX - 1, posY + fhDiv2 - 1, 0).endVertex();
        }
        VboUtil.drawVertexes(GL11.GL_QUADS, vb);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
