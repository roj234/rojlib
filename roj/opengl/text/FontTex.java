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

import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import roj.collect.*;
import roj.io.NIOUtil;
import roj.opengl.texture.TextureManager;
import roj.opengl.util.Util;
import roj.text.CharList;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.function.IntFunction;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Roj234
 * @since  2021/2/3 21:47
 */
public class FontTex {
    public static final class Tex {
        public final int textureId;
        public final int width, height, bottom;
        public final float u1, v1, u2, v2;

        public Tex(int textureId, Rectangle rect, int boundaryHeight, float x, float y) {
            this.textureId = textureId;
            this.width = rect.width;
            this.height = rect.height;
            // boundary: 32 while top = 10 means (22 - height) px for bottom
            this.bottom = boundaryHeight - rect.y - rect.height;
            this.u1 = x / TEXTURE_SIZE;
            this.v1 = y / TEXTURE_SIZE;
            this.u2 = (x + width) / TEXTURE_SIZE;
            this.v2 = (y + height) / TEXTURE_SIZE;
        }
    }

    public static final int TEXTURE_SIZE = 256;
    public static final int GLYPH_BORDER = 1;
    public static final int LAYOUT_FLAGS = Font.LAYOUT_LEFT_TO_RIGHT;
    public static final Font DEFAULT_FONT;

    static {
        /* Use Java's logical font as the default initial font if user does not override it in some configuration file */
        GraphicsEnvironment.getLocalGraphicsEnvironment().preferLocaleFonts();
        DEFAULT_FONT = new Font(null, Font.PLAIN, 24);
    }

    private static final Color TRANSPARENT = new Color(1, 1, 1, 0);
    private static final IntFunction<IntList> WII = value -> new IntList();

    private static BufferedImage charTmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
    private static Graphics2D charGraphics = charTmpImg.createGraphics();
    private static FontRenderContext charFRC = charGraphics.getFontRenderContext();

    private static void resizeCharTmp(int w, int h) {
        charTmpImg = new BufferedImage(w + 2, h, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D cg = charGraphics = charTmpImg.createGraphics();
        cg.setBackground(TRANSPARENT);

        cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        cg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        cg.setPaint(Color.WHITE);

        charFRC = cg.getFontRenderContext();
    }

    public final Font font;

    public final BufferedImage textureImage = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_BYTE_GRAY);
    private final Graphics2D textureGraphics = textureImage.createGraphics();

    /**
     * ID of current OpenGL cache texture being used by cacheGlyphs() to store pre-rendered glyph images.
     * efficiency
     */
    private int textureId = -1;
    private final Int2IntMap textureIds = new Int2IntMap();
    private final byte transparentFilter;

    private final CharMap<Tex> charTexture = new CharMap<>();
    private final CharMap<IntList> byWidth = new CharMap<>();

    private int currImgX, currImgY, currLineHeight;

    public FontTex(String nameAndCfg, int transparentFilter) {
        if(nameAndCfg != null) {
            this.font = Font.decode(nameAndCfg);
        } else {
            this.font = DEFAULT_FONT;
        }

        textureGraphics.setBackground(TRANSPARENT);
        textureGraphics.setComposite(AlphaComposite.Src);

        this.transparentFilter = (byte) transparentFilter;
    }

    public FontTex(String nameAndCfg) {
        this(nameAndCfg, 0xA0);
    }

    public Tex getEntry(char c) {
        Tex tex = charTexture.get(c);
        if(tex != null)
            textureIds.getEntry(tex.textureId).v = (int) System.currentTimeMillis();
        return tex;
    }

    public Tex getOrCreateEntry(char c) {
        if(!charTexture.containsKey(c)) {
            preRender(String.valueOf(c), 0, 1);
        }
        Tex tex = charTexture.get(c);
        if(tex != null)
            textureIds.getEntry(tex.textureId).v = (int) System.currentTimeMillis();
        return tex;
    }

    public void invalidateCache(int delta) {
        int v = (int) (System.currentTimeMillis() - delta);
        for (Iterator<Int2IntMap.Entry> itr = textureIds.entrySet().iterator(); itr.hasNext(); ) {
            Int2IntMap.Entry entry = itr.next();
            if (entry.v < v) {
                glDeleteTextures(entry.getKey());
                itr.remove();
            }
        }
    }

    public IntList getEntriesByWidth(int w) {
        IntList list = byWidth.get((char) w);
        return list == null ? new IntList() : list;
    }

    public Font getFont() {
        return font;
    }

    @Override
    public void finalize() {
        if(textureIds.size() == 0) return;
        for (PrimitiveIterator.OfInt itr = textureIds.keySet().iterator(); itr.hasNext(); ) {
            glDeleteTextures(itr.nextInt());
        }
        textureIds.clear();
        charTexture.clear();
        byWidth.clear();
    }

    public void dumpTexture() {
        glDisable(GL_CULL_FACE);

        int z = 100;
        for (PrimitiveIterator.OfInt itr = textureIds.keySet().iterator(); itr.hasNext(); ) {
            glBindTexture(GL_TEXTURE_2D, itr.nextInt());
            glBegin(GL_QUADS);
            glVertex3i(50, -50, z);
            glTexCoord2f(0, 0);
            glVertex3i(50, 50, z);
            glTexCoord2f(1, 0);
            glVertex3i(-50, 50, z);
            glTexCoord2f(1, 1);
            glVertex3i(-50, -50, z);
            glTexCoord2f(0, 1);
            glEnd();
            z += 20;
        }

        glEnable(GL_CULL_FACE);
    }

    public int preRender(CharSequence chars, int start, int length) {
        length += start;
        final CharMap<Tex> done = this.charTexture;
        IBitSet tmp = new LongBitSet();

        CharList noDuplicate = new CharList();
        for (int i = start; i < length; i++) {
            char c = chars.charAt(i);
            if(!done.containsKey(c) && tmp.add(c)) {
                noDuplicate.append(c);
            }
        }
        if(noDuplicate.length() > 0) {
            noDuplicate.append('|'); // 限制最低高度
            preRender0(noDuplicate.list, 0, noDuplicate.length());
        }
        return font.getSize();
    }

    private void preRender0(char[] chars, int start, int length) {
        if(textureId == -1) {
            textureId = allocateTexture();
        }

        GlyphVector vector = font.layoutGlyphVector(charFRC, chars, start, start + length, LAYOUT_FLAGS);
        int flag = vector.getLayoutFlags();
        if((flag & GlyphVector.FLAG_COMPLEX_GLYPHS) != 0) {
            System.err.println("[FontEx]Complex Char Glyph Detected");
        }

        int numGlyphs = vector.getNumGlyphs();

        for (int i = 0; i < numGlyphs; i++) {
            Point2D pos = vector.getGlyphPosition(i);
            pos.setLocation(pos.getX() + 2 * i, pos.getY());
            vector.setGlyphPosition(i, pos);
        }
        numGlyphs--;

        Rectangle bound = vector.getPixelBounds(charFRC, 0, 0);

        if (bound.width > charTmpImg.getWidth() || bound.height > charTmpImg.getHeight()) {
            resizeCharTmp(bound.width, bound.height);
        }

        charGraphics.clearRect(0, 0, bound.width, bound.height);
        charGraphics.drawGlyphVector(vector, -bound.x, -bound.y);

        Rectangle dirty = new Rectangle();

        int lineHeight = currLineHeight;

        int i, x = this.currImgX, y = this.currImgY;
        for (i = 0; i < numGlyphs; i++) {
            Rectangle rect = vector.getGlyphPixelBounds(i, null, -bound.x, -bound.y);

            if (x + rect.width + GLYPH_BORDER > TEXTURE_SIZE) {
                x = GLYPH_BORDER;
                y += lineHeight + GLYPH_BORDER;
                lineHeight = 0;
            }

            if (y + rect.height + GLYPH_BORDER > TEXTURE_SIZE) {
                break;
            }

            if (rect.height > lineHeight) {
                lineHeight = rect.height;
            }

            textureGraphics.drawImage(charTmpImg,
                    x, y, x + rect.width, y + rect.height,
                    rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                    null);

            charTexture.put(chars[start + i], new Tex(textureId, rect, bound.height, x, y));

            byWidth.computeIfAbsent((char) rect.width, WII).add(chars[start + i]);

            rect.setLocation(x, y);
            dirty.add(rect);

            x += rect.width + GLYPH_BORDER;
        }

        updateTexture(dirty, textureId);

        if(i < numGlyphs) {
            // 超高, 搞一个新的材质
            this.textureId = -1;
            this.currImgX = 0;
            this.currImgY = 0;
            this.currLineHeight = 0;
            preRender0(chars, start + i, length);
        } else {
            this.currLineHeight = lineHeight;
            this.currImgX = x;
            this.currImgY = y;
        }
    }

    private void updateTexture(Rectangle dirty, int textureId) {
        int prevTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        Util.bindTexture(textureId);

        // ! 向纹理中写入数据的时候不清空其内部的数据在特定情况可能会降低性能
        synchronized (TextureManager.UPLOAD_LOCK) {
            ByteBuffer buf = TextureManager.uploadImage(textureImage, dirty.x, dirty.y, dirty.width, dirty.height);
            if(transparentFilter != 0x00) {
                int target = transparentFilter & 0xFF;
                for (int i = 0; i < buf.limit(); i += 4) {
                    if((buf.get(i + 3) & 0xFF) < target) {
                        buf.put(i + 3, (byte) 0);
                    }
                }
            }

            glTexSubImage2D(GL_TEXTURE_2D, 0,
                                 dirty.x, dirty.y, dirty.width, dirty.height,
                                 GL12.GL_BGRA, GL_UNSIGNED_BYTE, buf);

            if(buf != TextureManager.UPLOAD_LOCK) {
                NIOUtil.clean(buf);
            }
        }

        /* Re-generate mipmap */
        GL30.glGenerateMipmap(GL_TEXTURE_2D);

        Util.bindTexture(prevTexture);
    }

    private int allocateTexture() {
        //Initialize the background.
        textureGraphics.clearRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE);

        //Allocate new OpenGL texture
        textureIds.put(textureId = glGenTextures(), (int) System.currentTimeMillis());

        int prevTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        Util.bindTexture(textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
        // 上传空图像, alpha8: 256级透明度
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL12.GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        Util.bindTexture(prevTexture);

        return textureId;
    }
}
