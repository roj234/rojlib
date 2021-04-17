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

package ilib.client.resource;

import ilib.client.util.RenderUtils;
import org.lwjgl.opengl.GL11;
import roj.io.IOUtil;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.GIFDecoder;
import roj.util.GIFDecoder.Gif;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.*;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class GifImage {
    private int width, height;
    private int tick, frame;
    private int glTextureId;
    private int frameCount;
    private int[] frameTicks;

    static {
        GIFDecoder.COLOR_TYPE = GIFDecoder.ARGB;

        File cachePath = new File("logs/.mi_gif_cache");
        if (!cachePath.exists() && !cachePath.mkdirs()) {
            throw new RuntimeException("Unable to create cache path");
        }
    }

    private static File getFile(String path) {
        return new File("logs/.mi_gif_cache/" + path.replace('\\', '_').replace('/', '_').replace(':', '_'));
    }

    public GifImage(ResourceLocation res) {
        File file;
        if ((file = getFile(res.toString())).exists()) {
            if (loadFromCache(file))
                return;
        }

        Gif gif;
        try (InputStream stream = new BufferedInputStream(Minecraft.getMinecraft().getResourceManager().getResource(res).getInputStream())) {
            gif = GIFDecoder.decode(IOUtil.read(stream));
        } catch (IOException | NullPointerException e) {
            throw new IllegalArgumentException("Error loading gif file: " + res, e);
        }
        this.width = gif.width;
        this.height = gif.height;

        init(gif, file);
    }

    private boolean loadFromCache(File info) {
        if (!info.exists()) return false;
        try (InputStream s = new FileInputStream(info)) {
            ByteReader r = new ByteReader(IOUtil.read(s));
            if (r.readInt() != 0x23336688)
                System.err.println("Invalid cache file header");
            String fileName = r.readString();
            if (!info.getName().equals(fileName))
                System.err.println("File name " + fileName + " does not fit " + info);
            this.width = r.readInt();
            this.height = r.readInt();
            this.frameTicks = new int[this.frameCount = r.readUnsignedShort()];
            for (int i = 0; i < frameCount; i++) {
                frameTicks[i] = r.readUnsignedShort();
            }
            BufferedImage image = ImageIO.read(r.getBytes().asInputStream());

            uploadTexture(image);

            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void finalize() {
        if (this.glTextureId != -1) {
            GL11.glDeleteTextures(this.glTextureId);
            this.glTextureId = -1;
        }
    }

    private void uploadTexture(BufferedImage image) {
        if (this.glTextureId == -1)
            this.glTextureId = GL11.glGenTextures();

        TextureUtil.uploadTextureImageAllocate(glTextureId, image, false, false);
    }

    static void copyImage(int[] canvas, int[] frame, int offsetX, int offsetY, int frameWidth, int frameHeight, int canvasWidth) {
        //System.out.println("frame size: " + frame.length);
        for (int canvasX = offsetX, frameX = 0, dx = offsetX + frameWidth; canvasX < dx; canvasX++, frameX++) {
            for (int canvasY = offsetY, frameY = 0, dy = offsetY + frameHeight; canvasY < dy; canvasY++, frameY++) {
                canvas[canvasY * canvasWidth + canvasX] = frame[frameY * frameWidth + frameX];
            }
        }
    }

    private void init(Gif gif, File file) {
        List<int[]> list = gif.toColorArray();

        BufferedImage allImage = new BufferedImage(gif.width * gif.frames.size(), gif.height, BufferedImage.TYPE_INT_ARGB);
        int[] imageBuffer = ((DataBufferInt) allImage.getRaster().getDataBuffer()).getData();

        int i = 0;
        for (int[] img : list) {
            copyImage(imageBuffer, img, i * gif.width, 0, gif.width, gif.height, gif.width * gif.frames.size());
            i++;
        }

        this.frameTicks = new int[frameCount = gif.frames.size()];
        i = 0;
        for (GIFDecoder.Frame fr : gif.frames) {
            frameTicks[i++] = fr.delay;
        }

        uploadTexture(allImage);

        try (OutputStream fos = new FileOutputStream(file)) {
            ByteWriter w = new ByteWriter(imageBuffer.length << 4);
            w.writeInt(0x23336688);
            w.writeString(file.toString());
            w.writeInt(this.width).writeInt(this.height).writeShort(frameCount);
            for (int tick : frameTicks) {
                w.writeShort(tick);
            }
            w.list.writeToStream(fos);
            ImageIO.write(allImage, "png", fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void render() {
        GlStateManager.bindTexture(this.glTextureId);
        GlStateManager.rotate(180.0f, 0.0F, 0.0F, 1.0F);

        RenderUtils.fastRect(0, 0, frame * width, 0, width, height, width * frameCount, height);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
    }

    protected int tickPerFrame(int frame) {
        return this.frameTicks[frame];
    }

    protected int frameCount() {
        return this.frameCount;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
