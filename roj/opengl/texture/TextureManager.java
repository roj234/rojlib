/*
 * This file is a part of MoreItems
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
package roj.opengl.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import roj.collect.ToIntMap;
import roj.io.NonblockingUtil;
import roj.opengl.util.Util;

import java.awt.image.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * 简易材质管理器
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/19 17:34
 */
public abstract class TextureManager {
    public static final int TEXTURE_SIZE = 256;
    private int nullTextureId = -1;

    private final ToIntMap<String> textureIds = new ToIntMap<>();

    public void bindTexture(String id) {
        int i = textureIds.getOrDefault(id, -1);
        if(i == -1) {
            BufferedImage image = getTexture(id);
            if(image == null) {
                if(nullTextureId == -1)
                    initNullTexture();
                i = nullTextureId;
            } else {
                i = GL11.glGenTextures();
                uploadTexture(image, i, 4);
            }
            textureIds.putInt(id, i);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, i);
    }

    private void initNullTexture() {
        if(nullTextureId != -1) return;
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x000000);
        img.setRGB(1, 0, 0xFF00FF);
        img.setRGB(0, 1, 0xFF00FF);
        img.setRGB(1, 1, 0x000000);
        int i = GL11.glGenTextures();
        uploadTexture(img, i, 4);
        nullTextureId = i;
    }

    public void freeTexture(String id) {
        Integer i = textureIds.remove(id);
        if(i != null) {
            GL11.glDeleteTextures(i);
        }
    }

    @Override
    public void finalize() {
        if(textureIds.isEmpty()) return;
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * textureIds.size()).order(ByteOrder.nativeOrder());
        IntBuffer dst = buffer.asIntBuffer();
        for (ToIntMap.Entry<String> entry : textureIds.selfEntrySet()) {
            dst.put(entry.v);
        }
        GL11.glDeleteTextures(dst);
        NonblockingUtil.clean(buffer);
    }

    private static final ByteBuffer UPLOADER = ByteBuffer.allocateDirect(4 * TEXTURE_SIZE * TEXTURE_SIZE).order(ByteOrder.BIG_ENDIAN);
    private static final int[] UPLOADER_RGB = new int[TEXTURE_SIZE * TEXTURE_SIZE];
    public static final Object UPLOAD_LOCK = UPLOADER;

    public static void uploadTexture(BufferedImage image, int textureId, int mipmap) {
        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Util.bindTexture(textureId);

        synchronized (UPLOADER) {
            ByteBuffer buf = uploadImage(image, 0, 0, image.getWidth(), image.getHeight());

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA8,
                              image.getWidth(), image.getHeight(), 0,
                              image.getColorModel().hasAlpha() ? GL11.GL_RGBA : GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE,
                              buf);

            if(buf != UPLOADER) {
                NonblockingUtil.clean(buf);
            }
        }

        if(mipmap > 0) {
            //  Mipmap is supported here
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmap);
            //GL11.glPrioritizeTextures();
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }

        Util.bindTexture(prevTexture);
    }

    public static ByteBuffer uploadImage(BufferedImage img, int x, int y, int width, int height) {
        /* Copy int array to direct buffer; big-endian order ensures a 0xRR, 0xGG, 0xBB, 0xAA byte layout */
        ByteBuffer up;
        if(width * height > TEXTURE_SIZE * TEXTURE_SIZE * 4) {
            up = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.BIG_ENDIAN);
        } else {
            up = UPLOADER;
            up.clear();
        }

        if (x != 0 || y != 0 || width != img.getWidth() || height != img.getHeight() || !(img.getColorModel() instanceof DirectColorModel)) {
            int[] buffer = img.getRGB(x, y, width, height, width * height > TEXTURE_SIZE * TEXTURE_SIZE ? null : UPLOADER_RGB, 0, width);
            up.position(0).limit(up.asIntBuffer().put(buffer).position() << 2);
        } else {
            DataBuffer db = img.getRaster().getDataBuffer();
            switch (db.getDataType()) {
                case DataBuffer.TYPE_BYTE:
                    up.put(((DataBufferByte) db).getData()).flip();
                    break;
                case DataBuffer.TYPE_USHORT:
                    up.position(0).limit(up.asShortBuffer().put(((DataBufferUShort) db).getData()).position() << 1);
                    break;
                case DataBuffer.TYPE_SHORT:
                    up.position(0).limit(up.asShortBuffer().put(((DataBufferShort) db).getData()).position() << 1);
                    break;
                case DataBuffer.TYPE_INT:
                    up.position(0).limit(up.asIntBuffer().put(((DataBufferInt) db).getData()).position() << 2);
                    break;
            }
        }
        return up;
    }

    /**
     * 遇到错误返回null，自动处理
     */
    protected abstract BufferedImage getTexture(String id);
}
