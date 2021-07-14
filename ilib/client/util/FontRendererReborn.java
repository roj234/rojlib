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
package ilib.client.util;

import ilib.asm.util.IFontRenderer;
import ilib.asm.util.MethodEntryPoint;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import roj.collect.IntList;
import roj.text.TextUtil;

import java.util.Random;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/3 19:40
 */
public class FontRendererReborn {
    private final int[] colorCode;
    private int color, lastTexture;
    private boolean colorUpdated;

    public boolean unicodeFlag;
    public float alpha = 1.0f;

    private final MCFont fontUsing;
    private final Random fontRandom;
    private final IFontRenderer delegate;

    public FontRendererReborn(MCFont font, FontRenderer renderer) {
        this.fontUsing = font;
        this.delegate = (IFontRenderer) renderer;
        this.colorCode = ((IFontRenderer)renderer).getColorCodeRawArray();
        this.fontRandom = ((IFontRenderer)renderer).getFontRandom();
    }

    public float render(String text, boolean shadow, float posX, float posY) {
        final int len = text.length();
        int lineHeight = fontUsing.preRender(text, 0, len);
        lastTexture = 0x80000000;
        color = 0xFFFFFF;

        int flag = 0;

        int lastFlag;
        float strikePos = 0, lastX = posX;

        for(int i = 0; i < len; ++i) {
            lastFlag = flag & 6;
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < len) {
                if(MethodEntryPoint.COLOR_CODE.contains(c = Character.toLowerCase(text.charAt(i + 1)))) {
                    if (MethodEntryPoint.HEX_CHAR.contains(c)) {
                        flag = 0;
                        int index = TextUtil.getNumber(c);
                        if(index == -1)
                            index = c - 'a' + 10;

                        if (shadow) {
                            index += 16;
                        }

                        this.color = this.colorCode[index];
                        this.colorUpdated = true;
                    } else {
                        switch (c) {
                            case 'r': {
                                flag = 0;
                                this.color = 0xFFFFFF;
                                this.colorUpdated = true;
                            }
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
                    ++i;
                    continue;
                }
            }

            int index = MethodEntryPoint.AWFUL_ASCII_ID.getOrDefault(c, -1);
            if ((flag & 16) != 0) {
                int w = this.getCharWidth(c);

                IntList list = fontUsing.getEntriesByWidth(w);
                c = (char) list.get(this.fontRandom.nextInt(list.size()));
            }

            float offset = index != -1 && !this.unicodeFlag ? 1 : 0.5F;

            shadow &= (c == 0 || index == -1 || this.unicodeFlag);
            if (shadow) {
                posX -= offset;
                posY -= offset;
            }

            float xLen = this.renderChar(c, posX, posY, (flag & 8) != 0 ? 1 : 0);

            if (shadow) {
                posX += offset;
                posY += offset;
            }

            if ((flag & 1) != 0) { // bold
                if (shadow) {
                    posY -= offset;
                } else {
                    posX += offset;
                }

                this.renderChar(c, posX, posY, (flag & 8) != 0 ? 1 : 0);

                if (shadow) {
                    posY += offset;
                } else {
                    posX -= offset;
                }

                ++xLen;
            }

            if(lastFlag != (flag & 6)) { // flag does changed
                if(lastFlag != 0) {
                    // flush and prepare
                    float backX = posX;
                    posX = lastX;
                    this.doDraw(lastFlag, posX, posY, strikePos, lineHeight);
                    posX = lastX = backX;
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
            posX += (int)xLen;
        }

        lastTexture = 0;
        return posX;
    }

    public int getCharWidth(char c) {
        switch (c) {
            case 160:
            case ' ':
                return 4;
            case 167:
                return -1;
        }
        int i;
        if (c > 0 && (i = MethodEntryPoint.AWFUL_ASCII_ID.getOrDefault(c, -1)) != -1 && !this.unicodeFlag) {
            return delegate.getAsciiWidth(i);
        } else {
            return fontUsing.getOrCreateEntry(c).width;
        }
    }

    private float renderChar(char ch, float posX, float posY, float italic) {
        switch (ch) {
            case 160:
            case ' ':
                return 4;
        }
        if(colorUpdated) {
            RenderUtils.setColor(color, alpha);
            colorUpdated = false;
        }
        int i = MethodEntryPoint.AWFUL_ASCII_ID.getOrDefault(ch, -1);
        if(i != -1 && !this.unicodeFlag) {
            boolean bind = false;
            if((lastTexture & 0x7FFFFFFF) != 0x7FFFFFFF) {
                bind = true;
                if((lastTexture & 0x80000000) != 0)
                    lastTexture = 0x80000000 | 0x7FFFFFFF;
            }
            return delegate.renderAsciiChar(i, posX, posY, italic, bind);
        }
        return this.renderUnicodeChar(ch, posX, posY, italic);
    }

    protected float renderUnicodeChar(char ch, float sx, float sy, float italic) {
        MCFont.Tex tex = fontUsing.getOrCreateEntry(ch);
        if (tex == null) {
            return 0;
        } else {
            if((lastTexture & 0x7FFFFFFF) != tex.textureId) {
                GlStateManager.bindTexture(tex.textureId);
                if((lastTexture & 0x80000000) != 0)
                    lastTexture = 0x80000000 | tex.textureId;
            }

            float width = (float) tex.width / 2F;
            float height = (float) tex.height / 2F;

            BufferBuilder builder = RenderUtils.BUILDER;
            builder.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX);
            builder.pos(sx + italic, sy, 0).tex(tex.u1, tex.v1).endVertex();
            builder.pos(sx - italic, sy + height, 0).tex(tex.u1, tex.v2).endVertex();
            builder.pos(sx + width + italic, sy, 0).tex(tex.u2, tex.v2).endVertex();
            builder.pos(sx + width - italic, sy + height, 0).tex(tex.u2, tex.v1).endVertex();
            RenderUtils.TESSELLATOR.draw();
            return width;
        }
    }

    protected void doDraw(int flag, float posX, float posY, float len, float lineHeight) {
        BufferBuilder builder = RenderUtils.BUILDER;

        GlStateManager.disableTexture2D();

        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

        if ((flag & 2) != 0) {
            // todo test builder 原先在这里, 分开了
            final float fhDiv2 = lineHeight / 2;
            builder.pos(posX, posY + fhDiv2, 0).endVertex();
            builder.pos(posX + len, posY + fhDiv2, 0).endVertex();
            builder.pos(posX + len, posY + fhDiv2 - 1, 0).endVertex();
            builder.pos(posX, posY + fhDiv2 - 1, 0).endVertex();
        }

        if ((flag & 4) != 0) {
            builder.pos(posX - 1, posY + lineHeight, 0).endVertex();
            builder.pos(posX + len, posY + lineHeight, 0).endVertex();
            builder.pos(posX + len, posY + lineHeight - 1, 0).endVertex();
            builder.pos(posX - 1, posY + lineHeight - 1, 0).endVertex();
        }
        RenderUtils.TESSELLATOR.draw();

        GlStateManager.enableTexture2D();
    }
}
