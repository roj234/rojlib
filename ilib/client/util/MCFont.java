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

import ilib.ImpLib;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import roj.collect.CharMap;
import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.text.CharList;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.IntFunction;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/3 21:47
 */
public class MCFont {
    private static final Marker MARKER = MarkerManager.getMarker("MCFONT");

    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 512;

    private static final int GLYPH_BORDER = 1;

    private static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    private static final List<Font> allFonts = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());

    private static final MyHashMap<String, MCFont> fontCache = new MyHashMap<>();

    private static BufferedImage charTmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static Graphics2D charGraphics = charTmpImg.createGraphics();
    private static FontRenderContext charFRC = charGraphics.getFontRenderContext();

    private static BufferedImage textureImage = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    private static Graphics2D textureGraphics = textureImage.createGraphics();

    private static IntBuffer argbBuffer = ByteBuffer.allocateDirect(4 * TEXTURE_WIDTH * TEXTURE_HEIGHT).order(ByteOrder.BIG_ENDIAN).asIntBuffer();

    /**
     * ID of current OpenGL cache texture being used by cacheGlyphs() to store pre-rendered glyph images.
     * efficiency
     */
    private int textureId = -1;
    private final IntList textureIds = new IntList();

    private final CharMap<Tex> charTexture = new CharMap<>();
    private final CharMap<IntList> byWidth = new CharMap<>();

    private static IntFunction<IntList> widthListSup = value -> new IntList();

    public Tex getEntry(char c) {
        return charTexture.get(c);
    }

    public Tex getOrCreateEntry(char c) {
        if(!charTexture.containsKey(c)) {
            preRender(String.valueOf(c), 0, 1);
        }
        return charTexture.get(c);
    }

    public IntList getEntriesByWidth(int w) {
        IntList list = byWidth.get((char) w);
        return list == null ? new IntList() : list;
    }

    public static class Tex {
        public Tex(int textureId) {
            this.textureId = textureId;
        }

        Tex wh(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        Tex uv(float x, float y) {
            this.u1 = x / TEXTURE_WIDTH;
            this.v1 = y / TEXTURE_HEIGHT;
            this.u2 = (x + width) / TEXTURE_WIDTH;
            this.v2 = (y + height) / TEXTURE_HEIGHT;
            return this;
        }

        int textureId;
        int width, height;
        int top;

        float u1, v1, u2, v2;
    }

    public static MCFont create(String fontName) {
        if(fontCache.containsKey(fontName)) {
            return fontCache.get(fontName);
        }
        return null;
    }

    static {
        /* Use Java's logical font as the default initial font if user does not override it in some configuration file */
        GraphicsEnvironment.getLocalGraphicsEnvironment().preferLocaleFonts();

        textureGraphics.setBackground(TRANSPARENT);
        textureGraphics.setComposite(AlphaComposite.Src);

        textureGraphics.setPaint(Color.WHITE);

        try {
            defaultFont = Font.createFont(Font.TRUETYPE_FONT, MCFont.class.getResourceAsStream("biliw.otf"));
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void resizeCharTmp(int w, int h) {
        charTmpImg = new BufferedImage(w + 2, h, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D cg = charGraphics = charTmpImg.createGraphics();
        cg.setBackground(TRANSPARENT);

        boolean transparent = true;
        cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                transparent ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                transparent ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        cg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        cg.setPaint(Color.WHITE);

        charFRC = cg.getFontRenderContext();
    }

    static Font defaultFont;
    Font font = defaultFont;
    int layoutFlags = Font.LAYOUT_LEFT_TO_RIGHT;
    int currImgX, currImgY;

    public MCFont(String fontName, float size) {
        if(fontName != null) {
            for (Font font : allFonts) {
                if(font.getFontName().equals(fontName)) {
                    this.font = font;
                    break;
                }
            }
        }

        if(font == defaultFont)
            ImpLib.logger().warn(MARKER, "Font " + fontName +  " couldn't be loaded.");

        font = font.deriveFont(size);
        fontCache.put(font.getName() + ':' + size, this);
    }

    @Override
    protected void finalize() {
        for (PrimitiveIterator.OfInt itr = textureIds.iterator(); itr.hasNext(); ) {
            GlStateManager.deleteTexture(itr.nextInt());
        }
    }

    public int preRender(CharSequence chars, int start, int length) {
        length += start;
        final CharMap<Tex> done = this.charTexture;

        CharList charList = new CharList();
        int j = 0;
        for (int i = start; i < length; i++) {
            char c = chars.charAt(i);
            if(!done.containsKey(c)) {
                charList.append(c);
            }
        }
        preRender0(charList.list, 0, j);
        return font.getSize();
    }

    public void preRender0(char[] chars, int start, int length) {
        if(textureId == -1) {
            textureId = allocateTexture();
        }

        GlyphVector vector = font.layoutGlyphVector(charFRC, chars, start, start + length, layoutFlags);

        int numGlyphs = vector.getNumGlyphs();

        for (int i = 0; i < numGlyphs; i++) {
            Point2D pos = vector.getGlyphPosition(i);
            pos.setLocation(pos.getX() + 2 * i, pos.getY());
            vector.setGlyphPosition(i, pos);
        }

        Rectangle bound = vector.getPixelBounds(charFRC, 0, 0);

        if (bound.width > charTmpImg.getWidth() || bound.height > charTmpImg.getHeight()) {
            resizeCharTmp(bound.width, bound.height);
        }

        charGraphics.clearRect(0, 0, bound.width, bound.height);
        charGraphics.drawGlyphVector(vector, -bound.x, -bound.y);

        textureGraphics.clearRect(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        Rectangle dirty = new Rectangle();

        int textureId = this.textureId;
        int lineHeight = 0;

        int x = this.currImgX, y = this.currImgY;
        for (int i = 0; i < numGlyphs; i++) {
            Rectangle rect = vector.getGlyphPixelBounds(i, null, -bound.x, -bound.y);

            if (x + rect.width + GLYPH_BORDER > TEXTURE_WIDTH) {
                x = GLYPH_BORDER;
                y += lineHeight + GLYPH_BORDER;
                lineHeight = 0;
            }

            if (y + rect.height + GLYPH_BORDER > TEXTURE_HEIGHT) {
                // 超高, 搞一个新的材质
                this.textureId = -1;
                this.currImgX = 0;
                this.currImgY = 0;
                preRender(new CharList(chars), start + i, length);
                break;
            }

            if (rect.height > lineHeight) {
                lineHeight = rect.height;
            }

            textureGraphics.drawImage(charTmpImg,
                    x, y, x + rect.width, y + rect.height,
                    rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                    null);

            charTexture.put(chars[start + i], new Tex(textureId).wh(rect.width, rect.height).uv((float) x / TEXTURE_WIDTH, (float) y / TEXTURE_HEIGHT));

            byWidth.computeIfAbsent((char) rect.width, widthListSup).add(chars[start + i]);

            rect.setLocation(x, y);
            dirty.add(rect);

            x += rect.width + GLYPH_BORDER;
        }

        if(this.textureId == textureId) {
            this.currImgX = x;
            this.currImgY = y;
        }

        updateTexture(dirty, textureId);
    }

    private void updateTexture(Rectangle dirty, int textureId) {
        uploadImage(dirty.x, dirty.y, dirty.width, dirty.height);

        GlStateManager.bindTexture(textureId);

        /* Due to changes in 1.14+, so this ensure pixels are correctly stored from CPU to GPU */
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, dirty.width);
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); // 4 is RGBA

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, dirty.x, dirty.y, dirty.width, dirty.height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, argbBuffer);

        /* Auto generate mipmap texture */
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }

    private int allocateTexture() {
        //Initialize the background to all white but fully transparent.
        textureGraphics.clearRect(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        //Allocate new OpenGL texture
        argbBuffer.clear();
        GL11.glGenTextures(argbBuffer);
        int textureId = argbBuffer.get(0);
        textureIds.add(textureId);

        // Load imageBuffer with pixel data ready for transfer to OpenGL texture
        uploadImage(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // Initialize texture with the now cleared BufferedImage. Using a texture with GL_ALPHA8 internal format may result in
        // faster rendering since the GPU has to only fetch 1 byte per texel instead of 4 with a regular RGBA texture.

        GlStateManager.bindTexture(textureId);

        //   Due to changes in 1.14+, so this ensure pixels are correctly stored from CPU to GPU
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0); // 0 is unspecific
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); // 4 is RGBA, has 4 channels

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA8, TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, argbBuffer);

        //  Mipmap is supported here
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // todo 注释了,test
        // GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

        return textureId;
    }

    private void uploadImage(int x, int y, int width, int height) {
        int[] buffer = ((DataBufferInt) textureImage.getRaster().getDataBuffer()).getData();
        if (x != 0 || y != 0 || width != TEXTURE_WIDTH || height != TEXTURE_HEIGHT) {
            cp(buffer, buffer, x, y, width, height, TEXTURE_WIDTH);
        }

        /* Copy int array to direct buffer; big-endian order ensures a 0xRR, 0xGG, 0xBB, 0xAA byte layout */
        argbBuffer.clear();
        argbBuffer.put(buffer, 0, width * height);
        argbBuffer.flip();
    }

    static void cp(int[] canvas, int[] frame, int offsetX, int offsetY, int frameWidth, int frameHeight, int canvasWidth) {
        // Argb => rgba
        for (int canvasX = offsetX, frameX = 0, dx = offsetX + frameWidth; canvasX < dx; canvasX++, frameX++) {
            for (int canvasY = offsetY, frameY = 0, dy = offsetY + frameHeight; canvasY < dy; canvasY++, frameY++) {
                assert canvasY * canvasWidth + canvasX < frameY * frameWidth + frameX;
                int color = canvas[canvasY * canvasWidth + canvasX];
                frame[frameY * frameWidth + frameX] = (color << 8) | (color >>> 24);
            }
        }
    }
}
