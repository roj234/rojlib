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
package ilib.client.renderer;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.client.util.RenderUtils;
import org.lwjgl.opengl.GL11;
import roj.collect.MyHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class WaypointRenderer {
    static final ResourceLocation BEAM = new ResourceLocation("textures/entity/beacon_beam.png");

    public static final int MIN_RENDER_DISTANCE = 1;
    public static final int MAX_RENDER_DISTANCE = -1;

    public static final int MAX_OFFSET_DEGREES = 5;
    public static final int FADING_DISTANCE = 6;

    public static final double FONT_SIZE = 0.005d;
    public static final double MIN_SHOW_DISTANCE = 12d;

    public static final MyHashMap<String, Waypoint> store = new MyHashMap<>();

    public static class Waypoint {
        protected String name;
        protected Vec3d position;
        protected Integer dimension;
        protected int color;

        public Waypoint(String name, Vec3d pos, Integer dimension, int color) {
            this.name = name;
            this.position = pos;
            this.dimension = dimension;
            this.color = color;
        }

        public Integer getDimension() {
            return this.dimension;
        }

        public Vec3d getPosition() {
            return this.position;
        }

        public String getName() {
            return this.name;
        }

        public int getColor() {
            return this.color;
        }
    }

    public static void reset() {
        store.clear();
    }

    public static void addIfNotPresent(String name, String display, int x, int y, int z, Integer dim, int color) {
        store.putIfAbsent(name, new Waypoint(display, new Vec3d(x, y, z), dim, color));
    }

    public static void add(String name, String display, int x, int y, int z, Integer dim, int color) {
        store.put(name, new Waypoint(display, new Vec3d(x, y, z), dim, color));
    }

    public static void remove(String name) {
        store.remove(name);
    }

    public static void render() {
        int dim = ClientProxy.mc.player.dimension;
        for (Waypoint wp : store.values()) {
            Integer i = wp.getDimension();
            if (i == null || i == dim)
                try {
                    doRender(wp);
                } catch (Throwable t) {
                    ImpLib.logger().error("During rendering Waypoint(s)");
                    t.printStackTrace();
                }
        }
    }

    static void doRender(Waypoint waypoint) {
        RenderManager man = RenderUtils.RENDER_MANAGER;
        if (man.renderViewEntity == null)
            return;

        Vec3d playerVec = man.renderViewEntity.getPositionVector();
        Vec3d waypointVec = waypoint.getPosition();
        double actualDistance = playerVec.distanceTo(waypointVec);

        if (MAX_RENDER_DISTANCE > 0 && actualDistance > MAX_RENDER_DISTANCE)
            return;

        float fadeAlpha = 1.0F;
        if (MIN_RENDER_DISTANCE > 0) {
            if (actualDistance <= MIN_RENDER_DISTANCE + 0.001f)
                return;
            if (FADING_DISTANCE > 0 && actualDistance <= MIN_RENDER_DISTANCE + FADING_DISTANCE)
                fadeAlpha = (float) ((actualDistance - MIN_RENDER_DISTANCE) / FADING_DISTANCE);
            if(fadeAlpha < 0 || fadeAlpha < .001f)
                return;
        }

        double viewDistance = actualDistance;
        double maxRenderDistance = (ClientProxy.mc.gameSettings.renderDistanceChunks << 4);
        if (viewDistance > maxRenderDistance) {
            Vec3d delta = waypointVec.subtract(playerVec).normalize();
            waypointVec = playerVec.add(delta.x * maxRenderDistance, delta.y * maxRenderDistance, delta.z * maxRenderDistance);
            viewDistance = maxRenderDistance;
        }

        double shiftX = waypointVec.x - man.viewerPosX;
        double shiftY = waypointVec.y - man.viewerPosY;
        double shiftZ = waypointVec.z - man.viewerPosZ;

        try {
            GlStateManager.pushMatrix();

            GL11.glEnable(GL11.GL_DEPTH_TEST);

            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            renderBeam(shiftX, -man.viewerPosY, shiftZ, waypoint.getColor(), fadeAlpha);

            if (viewDistance > MIN_SHOW_DISTANCE) {
                double yaw = Math.atan2(man.viewerPosZ - waypointVec.z, man.viewerPosX - waypointVec.x);
                float degrees = (float) Math.toDegrees(yaw) + 90;
                if (degrees < 0)
                    degrees += 360;
                float playerYaw = (man.renderViewEntity.getRotationYawHead() % 360.0F);
                if (playerYaw < 0)
                    playerYaw += 360;

                if(Math.abs(degrees - playerYaw) > MAX_OFFSET_DEGREES)
                    return;
            }

            double scale = FONT_SIZE * (0.6 * viewDistance + 12);
            StringBuilder sb = new StringBuilder();
            String label = waypoint.getName();
            if (label != null)
                sb.append("\u00a7l ").append(label).append(' ');
            label = sb.toString();
            String dist = String.valueOf(actualDistance);
            sb.delete(0, sb.length());
            dist = sb.append("( ").append(dist, 0, dist.indexOf('.') + 2).append("米 )").toString();

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            GlStateManager.translate(shiftX + 0.5, shiftY + 4, shiftZ + 0.5);
            RenderUtils.rotateToPlayer();
            GlStateManager.scale(-scale, -scale, scale);

            FontRenderer fr = ClientProxy.mc.fontRenderer;

            double lineHeight = fr.FONT_HEIGHT + (fr.getUnicodeFlag() ? 0 : 4);
            double labelWidth = fr.getStringWidth(label);
            double distWidth = fr.getStringWidth(dist);

            double maxWidth = Math.max(labelWidth, distWidth);
            // 居中
            // original: y=0 .....  maxWidth + 0 ..... 2.4
            RenderUtils.drawRectangle(-maxWidth * 0.5, 0.5, 0, maxWidth + 0.1, 2.2 * lineHeight, 0x111111, 255 - (int) (fadeAlpha * 127));

            GlStateManager.translate(labelWidth - Math.floor(labelWidth), 2, 0);
            drawLabel(label, labelWidth, waypoint.getColor() | (int) (fadeAlpha * 255) << 24);

            GlStateManager.translate(distWidth - Math.floor(distWidth), lineHeight + 1, 0);
            drawLabel(dist, distWidth, 0xffffff | (int) (fadeAlpha * 255) << 24);
        } finally {
            GlStateManager.popMatrix();

            GlStateManager.disableLighting();
            GlStateManager.enableLighting();

            GlStateManager.disableDepth();
            GlStateManager.enableDepth();
        }
    }

    public static void drawLabel(String text, double bgWidth, int color) {
        float textX = (float) (-bgWidth / 2.0D);
        ClientProxy.mc.fontRenderer.drawString(text, textX, 0, color, false);
    }

    static void renderBeam(double x, double y, double z, int color, float alpha) {
        RenderUtils.bindTexture(BEAM);

        int rgba = color | ((int) (alpha * 0.8f * 255) << 24);

        float time = ClientProxy.mc.isGamePaused() ? Minecraft.getSystemTime() / 50L : ClientProxy.mc.world.getTotalWorldTime();
        float texOffset = -(-time * 0.2F - MathHelper.floor(-time * 0.1F)) * 0.6F;

        double dy = (256 * alpha);
        double v2 = (-1 + texOffset);
        double v = (25 * alpha) + v2;

        double b = 0.8;
        double c = 0.2;

        RenderUtils.BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        v(x + c, y + dy, z + c, 1, v, rgba);
        v(x + c, y, z + c, 1, v2, rgba);
        v(x + b, y, z + c, 0.0D, v2, rgba);
        v(x + b, y + dy, z + c, 0.0D, v, rgba);
        v(x + b, y + dy, z + b, 1, v, rgba);
        v(x + b, y, z + b, 1, v2, rgba);
        v(x + c, y, z + b, 0.0D, v2, rgba);
        v(x + c, y + dy, z + b, 0.0D, v, rgba);
        v(x + b, y + dy, z + c, 1, v, rgba);
        v(x + b, y, z + c, 1, v2, rgba);
        v(x + b, y, z + b, 0.0D, v2, rgba);
        v(x + b, y + dy, z + b, 0.0D, v, rgba);
        v(x + c, y + dy, z + b, 1, v, rgba);
        v(x + c, y, z + b, 1, v2, rgba);
        v(x + c, y, z + c, 0.0D, v2, rgba);
        v(x + c, y + dy, z + c, 0.0D, v, rgba);

        RenderUtils.TESSELLATOR.draw();
    }

    private static void v(double x, double y, double z, double u, double v, int rgba) {
        RenderUtils.BUILDER.pos(x, y, z).tex(u, v).color(rgba >>> 24 & 255, rgba >>> 16 & 255, rgba >>> 8 & 255, rgba >>> 24 & 255);
    }
}
