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
package roj.opengl.altas;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import roj.collect.ToIntMap;
import roj.math.MathUtils;
import roj.opengl.FrameBuffer;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormats;

import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * TextureAltasSpritet
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/18 15:46
 */
public class TextureAtlas {
    private static final Comparator<PendingSprite> AREA_CP = (o1, o2) -> {
        int v = Integer.compare(o1.area(), o2.area());
        return v != 0 ? v : Boolean.compare(o1.image.getWidth() > o1.image.getHeight(), o2.image.getWidth() > o2.image.getHeight());
    };

    private final AtlasMap uvPosition = new AtlasMap();
    private BufferedImage unmatchedEdges;
    private int textureId;
    private int texSize;
    private final ArrayList<PendingSprite> pending;

    public TextureAtlas() {
        this(64);
    }

    public TextureAtlas(int initialWH) {
        this.texSize = MathUtils.getMin2PowerOf(initialWH);
        this.textureId = -1;
        this.pending = new ArrayList<>();
    }

    public void register(String id, BufferedImage texture) {
        if(uvPosition.containsKey(id)) throw new IllegalStateException("Already register");
        Sprite sprite = (Sprite) uvPosition.getOrCreateEntry(id);
        pending.add(new PendingSprite(sprite, texture));
    }

    public void bake() {
        if(pending.isEmpty()) return;
        if(textureId == -1) {
            textureId = GL11.glGenTextures();
            int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            Util.bindTexture(textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
            Util.bindTexture(prev);
        }
        // todo 我需要一种算法
        int areaRequired = 0;
        for (int i = 0; i < pending.size(); i++) {
            areaRequired += pending.get(i).area();
        }
        for (Iterator<ToIntMap.Entry<String>> itr = uvPosition.selfEntrySet().iterator(); itr.hasNext(); ) {
            Sprite sprite = (Sprite) itr.next();
            areaRequired += sprite.w * sprite.h;
        }
        int sz = this.texSize;
        if(sz * sz < areaRequired) {
            this.texSize = sz = MathUtils.getMin2PowerOf((int) Math.sqrt(areaRequired));
        }

        int lvl = -1;
        while (sz > 0) {
            sz >>>= 1;
            lvl++;
        }
        Stitcher stitcher = new Stitcher(0, 0, lvl);

        // 面积小，且宽度较大的放到前面
        pending.sort(AREA_CP);
        for (PendingSprite sprite : pending) {
            stitcher.push(sprite);
        }
        pending.clear();

        Rectangle dirty = stitcher.stitch();

        FrameBuffer fbo = new FrameBuffer(dirty.width, dirty.height);
        fbo.begin();

        VertexBuilder vb = new VertexBuilder(65536);
        vb.begin(VertexFormats.POSITION_TEX);
        stitcher.render(vb);
        VboUtil.drawVertexes(GL11.GL_QUADS, vb);

        Util.bindTexture(textureId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, dirty.x, dirty.y, 0, 0, dirty.width, dirty.height);

        fbo.end();
        fbo.deleteFBO();

        //if(!previousBaked) {
        //}
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        fbo.restoreTexture();
    }

    private static final class Stitcher {
        int x, y, level;
        List<Stitcher> subElements;

        public Stitcher(int x, int y, int level) {
            this.x = x;
            this.y = y;
            this.level = level;
        }

        public void push(PendingSprite sprite) {

        }

        public Rectangle stitch() {
            return null;
        }

        public void render(VertexBuilder vb) {

        }
    }

    private static final class PendingSprite {
        final Sprite owner;
        final BufferedImage image;

        private PendingSprite(Sprite owner, BufferedImage image) {
            this.owner = owner;
            this.image = image;
        }

        int area() {
            return image.getHeight() * image.getWidth();
        }
    }

    private static final class AtlasMap extends ToIntMap<String> {
        @Override
        protected Entry<String> newEntry(String id, int value) {
            return new Sprite(id, value);
        }

        @Override
        public Entry<String> getOrCreateEntry(String id) {
            return super.getOrCreateEntry(id);
        }
    }

    public static final class Sprite extends ToIntMap.Entry<String> {
        int w, h;
        float u1, v1, u2, v2;

        public Sprite(String k, int v) {
            super(k, v);
        }

        public int getWidth() {
            return w;
        }
        public int getHeight() {
            return h;
        }

        public float getUMin() {
            return u1;
        }

        public float getVMin() {
            return v1;
        }

        public float getUMax() {
            return u2;
        }

        public float getVMax() {
            return v2;
        }
    }
}
