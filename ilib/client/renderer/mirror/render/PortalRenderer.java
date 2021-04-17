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
package ilib.client.renderer.mirror.render;

import ilib.client.renderer.mirror.ClientEventHandler;
import ilib.client.renderer.mirror.MirrorSubSystem;
import ilib.client.renderer.mirror.portal.EntityTranStack;
import ilib.client.renderer.mirror.portal.Portal;
import ilib.client.renderer.mirror.render.world.RenderGlobalProxy;
import ilib.client.util.RenderUtils;
import ilib.util.ItemUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import roj.math.MathUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static ilib.ClientProxy.mc;

public class PortalRenderer {
    public static int renderLevel = 0;
    public static int renderCount = 0;
    public static int frameCount;
    public static List<Float> rollFactor = new ArrayList<>();

    public static float getRollFactor(int level, float partialTick) {
        float roll = MathUtils.interpolate(ClientEventHandler.prevCameraRoll, ClientEventHandler.cameraRoll, partialTick);
        if (level <= 0) {
            return roll;
        } else if (level <= rollFactor.size()) {
            for (int i = level - 1; i >= 0; i--) {
                roll += rollFactor.get(i);
            }
            return roll;
        }
        return 0F;
    }

    /**
     * @param portal World Portal we're rendering
     * @param entity Entity viewing the world portal
     * @param posRot applied offset from the pair
     * @param rotRot applied rotations from the pair
     */
    public static void renderWorldPortal(Portal portal, Entity entity, float[] posRot, float[] rotRot, float partialTick) {
        if (renderLevel <= MirrorSubSystem.maxRecursion - 1 && renderCount < MirrorSubSystem.maxRenderPerTick && portal.hasPair()) {
            renderLevel++;
            renderCount++;

            //set fields and render the stencil area.
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GlStateManager.colorMask(false, false, false, false);
            GlStateManager.depthMask(true);
            GlStateManager.color(1, 1, 1, 1);

            GL11.glStencilFunc(GL11.GL_ALWAYS, MirrorSubSystem.stencilValue + renderLevel, 0xFF); //set the stencil test to always pass, set reference value, set mask.
            if (renderLevel == 1)
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);  // sPass, dFail, dPass. Set stencil where the depth passes fails. (which means the render is the topmost - depth mask is on)
            else
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);  // sPass, dFail, dPass. Set stencil where the depth passes fails. (which means the render is the topmost - depth mask is on)
            GL11.glStencilMask(0xFF); //set the stencil mask.

            GL11.glClearStencil(0); //stencil clears to 0 when cleared.
            if (renderLevel == 1) GlStateManager.clear(GL11.GL_STENCIL_BUFFER_BIT);

            GlStateManager.disableTexture2D();
            //only draw the portal on first recursion, we shouldn't draw anything outside the portal.
            portal.drawPlane(partialTick);//draw to stencil to set the areas that pass stencil and depth to our reference value

            GL11.glStencilMask(0x00); //Disable drawing to the stencil buffer now.
            GL11.glStencilFunc(GL11.GL_EQUAL, MirrorSubSystem.stencilValue + renderLevel, 0xFF); //anything drawn now will only show if the value on the stencil equals to our reference value.
            //This is where we hope nothing would have done GL_INCR or GL_DECR where we've drawn our stuffs.

            //set the z-buffer to the farthest value for every pixel in the portal, before rendering the stuff in the portal
            GlStateManager.depthFunc(GL11.GL_ALWAYS);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3d(1, -1, 1);
            GL11.glVertex3d(1, 1, 1);
            GL11.glVertex3d(-1, 1, 1);
            GL11.glVertex3d(-1, -1, 1);
            GL11.glEnd();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);

            //reset the colour and gl states
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableNormalize();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            //After here

            //Add roll level
            rollFactor.add(rotRot[2]);

            //render world here
            drawWorld(mc, portal, entity, posRot, rotRot, partialTick);
            //End render world

            //remove roll level
            if (!rollFactor.isEmpty()) {
                rollFactor.remove(rollFactor.size() - 1);
            }

            //(stuff rendered at the local side of the portal after the portal is rendered should see the same depth value as if it's a simple normal quad)
            //This call fixes that
            //This also resets the stencil buffer to how it was previously before this function was called..
            GlStateManager.disableTexture2D();
            GL11.glColorMask(false, false, false, false);
            if (renderLevel > 1) {
                GL11.glStencilFunc(GL11.GL_ALWAYS, MirrorSubSystem.stencilValue + renderLevel - 1, 0xFF); //set the stencil test to always pass, set reference value, set mask.
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);  // sPass, dFail, dPass. Set stencil where the depth passes fails. (which means the render is the topmost - depth mask is on)
                GL11.glStencilMask(0xFF); //set the stencil mask.
            }
            portal.drawPlane(partialTick);
            if (renderLevel > 1) {
                GL11.glStencilMask(0x00); //Disable drawing to the stencil buffer now.
                GL11.glStencilFunc(GL11.GL_EQUAL, MirrorSubSystem.stencilValue + renderLevel - 1, 0xFF); //anything drawn now will only show if the value on the stencil equals to our reference value.
            }
            GL11.glColorMask(true, true, true, true);
            GlStateManager.enableTexture2D();

            if (renderLevel == 1) GL11.glDisable(GL11.GL_STENCIL_TEST);

            renderLevel--;
        }
    }

    private static void drawWorld(Minecraft mc, Portal portal, Entity viewing, float[] posOffset, float[] rotOffset, float partialTick) {
        Portal pair = portal.getPair();

        TileEntityRendererDispatcher.instance.drawBatch(MinecraftForgeClient.getRenderPass());

        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1F, 1F, 1F);
        boolean hideGui = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;

        double cameraZoom = mc.entityRenderer.cameraZoom;
        mc.entityRenderer.cameraZoom = 1.0125F; //Lightly narrow the FoV of the player view to reduce the rendering but it's not being very helpful :/

        int renderDist = mc.gameSettings.renderDistanceChunks;
        mc.gameSettings.renderDistanceChunks = portal.getRenderDistanceChunks();

        RenderGlobal global = mc.renderGlobal;
        RenderGlobalProxy proxy = ClientEventHandler.proxy;
        mc.renderGlobal = proxy;

        proxy.cloudTickCounter = global.cloudTickCounter;
        proxy.bindViewFrustum(pair); //binds to the View Frustum for this TE.
        proxy.displayListEntitiesDirty = true;
        proxy.storePlayerInfo();

        AxisAlignedBB pairFlatPlane = pair.getFlatPlane();
        double destX = (pairFlatPlane.maxX + pairFlatPlane.minX) / 2D;
        double destY = (pairFlatPlane.maxY + pairFlatPlane.minY) / 2D;
        double destZ = (pairFlatPlane.maxZ + pairFlatPlane.minZ) / 2D;

        ItemStack dualHandItem = ItemUtils.getUsableDualHandItem(mc.player);
        if (mc.gameSettings.thirdPersonView == 0) {
            if (!dualHandItem.isEmpty()) {
                mc.player.setActiveHand(dualHandItem.equals(mc.player.getHeldItem(EnumHand.MAIN_HAND)) ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND);
            } else {
                dualHandItem = null;
            }
        } else {
            dualHandItem = null;
        }

        EntityTranStack stack = new EntityTranStack(viewing).moveEntity(destX, destY, destZ, posOffset, rotOffset, partialTick);
        drawWorld(proxy, viewing, portal, partialTick);
        stack.pop();

        if (dualHandItem != null) {
            mc.player.resetActiveHand();
        }

        mc.gameSettings.renderDistanceChunks = renderDist;
        mc.renderGlobal = global;

        mc.entityRenderer.cameraZoom = cameraZoom;
        mc.gameSettings.hideGUI = hideGui;

        ForgeHooksClient.setRenderPass(0);

        double px = viewing.lastTickPosX + (viewing.posX - viewing.lastTickPosX) * partialTick;
        double py = viewing.lastTickPosY + (viewing.posY - viewing.lastTickPosY) * partialTick;
        double pz = viewing.lastTickPosZ + (viewing.posZ - viewing.lastTickPosZ) * partialTick;

        //TODO double check if this is still necessary after each individual WP gets it's own render global proxy.
        //Water render recursing portal fix
        if (renderLevel > 1) {
            proxy.bindViewFrustum(pair); //binds to the View Frustum for this TE.
            proxy.renderContainer.initialize(px, py, pz);

            proxy.lastViewEntityX = viewing.posX;
            proxy.lastViewEntityY = viewing.posY;
            proxy.lastViewEntityZ = viewing.posZ;
            proxy.lastViewEntityPitch = viewing.rotationPitch;
            proxy.lastViewEntityYaw = viewing.rotationYaw;
        }
        //Water render recursing portal fix end

        TileEntityRendererDispatcher.instance.prepare(mc.world, mc.getTextureManager(), mc.fontRenderer, viewing, mc.objectMouseOver, partialTick);
        RenderUtils.RENDER_MANAGER.cacheActiveRenderInfo(mc.world, mc.fontRenderer, viewing, mc.pointedEntity, mc.gameSettings, partialTick);
        RenderUtils.RENDER_MANAGER.renderPosX = px;
        RenderUtils.RENDER_MANAGER.renderPosY = py;
        RenderUtils.RENDER_MANAGER.renderPosZ = pz;

        TileEntityRendererDispatcher.staticPlayerX = px;
        TileEntityRendererDispatcher.staticPlayerY = py;
        TileEntityRendererDispatcher.staticPlayerZ = pz;

        ActiveRenderInfo.updateRenderInfo(mc.player, false); // view changes?

        ClippingHelperMy.getInstance();

        Particle.interpPosX = px;
        Particle.interpPosY = py;
        Particle.interpPosZ = pz;
        Particle.cameraViewDir = viewing.getLook(partialTick);

        GlStateManager.popMatrix();

        TileEntityRendererDispatcher.instance.preDrawBatch();
    }

    private static void drawWorld(RenderGlobalProxy proxy, Entity entity, Portal portal, float partialTick) {
        GlStateManager.enableCull();
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        GlStateManager.clearColor(0, 0, 0, 0);

        GlStateManager.enableTexture2D();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        double dx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) partialTick;
        double dy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) partialTick;
        double dz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) partialTick;

        EntityRenderer er = mc.entityRenderer;

        setupCameraTransform(er, entity, partialTick, 2);

        ActiveRenderInfo.updateRenderInfo(mc.player, false);
        ClippingHelperMy.getInstance();

        ICamera cam = new Frustum();
        cam.setPosition(dx, dy, dz);

        RenderHelper.disableStandardItemLighting();

        if (mc.gameSettings.renderDistanceChunks >= 4) {
            er.setupFog(-1, partialTick);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            Project.gluPerspective(er.getFOVModifier(partialTick, true), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, er.farPlaneDistance * 2);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            proxy.renderSky(partialTick, 2);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            Project.gluPerspective(er.getFOVModifier(partialTick, true), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, er.farPlaneDistance * MathHelper.SQRT_2);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }

        er.setupFog(0, partialTick);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        if (entity.posY + (double) entity.getEyeHeight() < 128) {
            er.renderCloudsCheck(proxy, partialTick, 2, dx, dy, dz);
        }

        er.setupFog(0, partialTick);
        RenderUtils.bindMinecraftBlockSheet();

        RenderHelper.disableStandardItemLighting();
        proxy.setupTerrain(entity, partialTick, cam, frameCount++, mc.player.isSpectator());

        int j = Math.min(Minecraft.getDebugFPS(), mc.gameSettings.limitFramerate);
        j = Math.max(j, 60);
        long l = Math.max(1000000000 / j / 4, 0L);
        mc.renderGlobal.updateChunks(System.nanoTime() + l);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.disableAlpha();
        proxy.renderBlockLayer(BlockRenderLayer.SOLID, partialTick, 2, entity);
        GlStateManager.enableAlpha();
        proxy.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, partialTick, 2, entity);

        ITextureObject texBlock = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        texBlock.setBlurMipmap(false, false);
        proxy.renderBlockLayer(BlockRenderLayer.CUTOUT, partialTick, 2, entity);
        texBlock.restoreLastBlurMipmap();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        RenderHelper.enableStandardItemLighting();
        ForgeHooksClient.setRenderPass(0);
        proxy.renderEntities(entity, cam, partialTick, portal);
        ForgeHooksClient.setRenderPass(0);
        RenderHelper.disableStandardItemLighting();
        er.disableLightmap();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        texBlock.setBlurMipmap(false, false);
        proxy.drawBlockDamageTexture(RenderUtils.TESSELLATOR, RenderUtils.BUILDER, entity, partialTick);
        texBlock.restoreLastBlurMipmap();
        GlStateManager.disableBlend();

        er.enableLightmap();
        Particle.interpPosX = dx;
        Particle.interpPosY = dy;
        Particle.interpPosZ = dz;
        Particle.cameraViewDir = entity.getLook(partialTick);
        float f = 0.017453292F;
        float f1 = MathHelper.cos(entity.rotationYaw * f);
        float f2 = MathHelper.sin(entity.rotationYaw * f);
        float f3 = -f2 * MathHelper.sin(entity.rotationPitch * f);
        float f4 = f1 * MathHelper.sin(entity.rotationPitch * f);
        float f5 = MathHelper.cos(entity.rotationPitch * f);

        ParticleManager pcman = mc.effectRenderer;

        Portal pair = portal.getPair();
        for (int i = 0; i < 2; ++i) {
            Queue<Particle> queue = pcman.fxLayers[3][i];

            if (!queue.isEmpty()) {
                for (Particle particle : queue) {
                    if (!portal.getCullRender() || canDraw(particle, pair.getFaceOn(), pair.getPos())) {
                        particle.renderParticle(RenderUtils.BUILDER, entity, partialTick, f1, f5, f2, f3, f4);
                    }
                }
            }
        }
        RenderHelper.disableStandardItemLighting();
        er.setupFog(0, partialTick);

        float ff = ActiveRenderInfo.getRotationX();
        float ff1 = ActiveRenderInfo.getRotationZ();
        float ff2 = ActiveRenderInfo.getRotationYZ();
        float ff3 = ActiveRenderInfo.getRotationXY();
        float ff4 = ActiveRenderInfo.getRotationXZ();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.003921569F);

        TextureManager txm = RenderUtils.TEXTURE_MANAGER;

        for (int layType = 0; layType < 3; ++layType) {
            for (int layCat = 0; layCat < 2; ++layCat) {
                ArrayDeque<Particle> particles = pcman.fxLayers[layType][layCat];
                if (!particles.isEmpty()) {
                    switch (layCat) {
                        case 0:
                            GlStateManager.depthMask(false);
                            break;
                        case 1:
                            GlStateManager.depthMask(true);
                    }

                    switch (layType) {
                        case 0:
                        default:
                            txm.bindTexture(ParticleManager.PARTICLE_TEXTURES);
                            break;
                        case 1:
                            txm.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    }

                    GlStateManager.color(1, 1, 1, 1);
                    RenderUtils.BUILDER.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);

                    for (final Particle particle : particles) {
                        if (!portal.getCullRender() || canDraw(particle, pair.getFaceOn(), pair.getPos())) {
                            particle.renderParticle(RenderUtils.BUILDER, entity, partialTick, ff, ff4, ff1, ff2, ff3);
                        }
                    }

                    RenderUtils.TESSELLATOR.draw();
                }
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(516, 0.1F);
        er.disableLightmap();

        GlStateManager.depthMask(false);
        GlStateManager.enableCull();
        er.renderRainSnow(partialTick);

        GlStateManager.depthMask(true);
        proxy.renderWorldBorder(entity, partialTick);

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        er.setupFog(0, partialTick);

        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        txm.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        proxy.renderBlockLayer(BlockRenderLayer.TRANSLUCENT, partialTick, 2, entity);

        RenderHelper.enableStandardItemLighting();
        ForgeHooksClient.setRenderPass(1);
        proxy.renderEntities(entity, cam, partialTick, portal);

        ForgeHooksClient.setRenderPass(-1);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableFog();

        if (entity.posY + (double) entity.getEyeHeight() >= 128) {
            er.renderCloudsCheck(proxy, partialTick, 2, dx, dy, dz);
        }

        ForgeHooksClient.dispatchRenderLast(proxy, partialTick);

        RenderHelper.enableStandardItemLighting();
    }

    //This is a modified copy of setupCameraTransform and orientCamera in EntityRenderer.
    private static void setupCameraTransform(EntityRenderer er, Entity entity, float partialTicks, int pass) {
        er.farPlaneDistance = (float) (mc.gameSettings.renderDistanceChunks * 16);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        float f = 0.07F;

        if (mc.gameSettings.anaglyph) {
            GlStateManager.translate((float) (-(pass * 2 - 1)) * f, 0, 0);
        }

        if (er.cameraZoom != 1) {
            GlStateManager.translate((float) er.cameraYaw, (float) (-er.cameraPitch), 0);
            GlStateManager.scale(er.cameraZoom, er.cameraZoom, 1);
        }

        Project.gluPerspective(er.getFOVModifier(partialTicks, true), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, er.farPlaneDistance * MathHelper.SQRT_2);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();

        if (mc.gameSettings.anaglyph) {
            GlStateManager.translate((float) (pass * 2 - 1) * 0.1F, 0, 0);
        }

        er.hurtCameraEffect(partialTicks);

        if (mc.gameSettings.viewBobbing) {
            er.applyBobbing(partialTicks);
        }

        float eyeHeight = entity.getEyeHeight();

        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isPlayerSleeping()) {
            eyeHeight = (float) ((double) eyeHeight + 1);
            GlStateManager.translate(0, 0.3F, 0);

            if (!mc.gameSettings.debugCamEnable) {
                BlockPos blockpos = new BlockPos(entity);
                IBlockState iblockstate = mc.world.getBlockState(blockpos);
                ForgeHooksClient.orientBedCamera(mc.world, blockpos, iblockstate, entity);
                //TODO does the bed camera affect the portal view? Is this required?

                GlStateManager.rotate(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks + 180, 0, -1, 0);
                GlStateManager.rotate(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks, -1, 0, 0);
            }
        } else if (mc.gameSettings.thirdPersonView > 0) {
            double d3 = er.thirdPersonDistancePrev + (er.thirdPersonDistance - er.thirdPersonDistancePrev) * partialTicks;

            if (mc.gameSettings.debugCamEnable) {
                GlStateManager.translate(0, 0, (float) (-d3));
            } else {
                float f1 = entity.rotationYaw;
                float f2 = entity.rotationPitch;

                if (mc.gameSettings.thirdPersonView == 2) {
                    f2 += 180;
                }

                if (er.mc.gameSettings.thirdPersonView == 2) {
                    GlStateManager.rotate(180, 0, 1, 0);
                }

                GlStateManager.rotate(entity.rotationPitch - f2, 1, 0, 0);
                GlStateManager.rotate(entity.rotationYaw - f1, 0, 1, 0);
                GlStateManager.translate(0, 0, (float) (-d3));
                GlStateManager.rotate(f1 - entity.rotationYaw, 0, 1, 0);
                GlStateManager.rotate(f2 - entity.rotationPitch, 1, 0, 0);
            }
        } else {
            GlStateManager.translate(0, 0, 0.05F);
        }

        if (!mc.gameSettings.debugCamEnable) {
            float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks + 180;
            float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
            float roll = getRollFactor(renderLevel, partialTicks);
            if (entity instanceof EntityAnimal) {
                EntityAnimal entityanimal = (EntityAnimal) entity;
                yaw = entityanimal.prevRotationYawHead + (entityanimal.rotationYawHead - entityanimal.prevRotationYawHead) * partialTicks + 180;
            }
            IBlockState state = ActiveRenderInfo.getBlockStateAtEntityViewpoint(er.mc.world, entity, partialTicks);

            EntityViewRenderEvent.CameraSetup event = new EntityViewRenderEvent.CameraSetup(er, entity, state, partialTicks, yaw, pitch, roll);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            GlStateManager.rotate(event.getRoll(), 0, 0, 1);
            GlStateManager.rotate(event.getPitch(), 1, 0, 0);
            GlStateManager.rotate(event.getYaw(), 0, 1, 0);
        }

        GlStateManager.translate(0, -eyeHeight, 0);
        double d0 = entity.prevPosX + (entity.posX - entity.prevPosX) * (double) partialTicks;
        double d1 = entity.prevPosY + (entity.posY - entity.prevPosY) * (double) partialTicks + (double) eyeHeight;
        double d2 = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * (double) partialTicks;
        er.cloudFog = mc.renderGlobal.hasCloudFog(d0, d1, d2, partialTicks);

        if (er.debugView) {
            switch (er.debugViewDirection) {
                case 0:
                    GlStateManager.rotate(90, 0, 1, 0);
                    break;
                case 1:
                    GlStateManager.rotate(180, 0, 1, 0);
                    break;
                case 2:
                    GlStateManager.rotate(-90, 0, 1, 0);
                    break;
                case 3:
                    GlStateManager.rotate(90, 1, 0, 0);
                    break;
                case 4:
                    GlStateManager.rotate(-90, 1, 0, 0);
            }
        }
    }

    private static boolean canDraw(Particle ent, EnumFacing face, BlockPos pos) {
        switch (face) {
            case UP:
                return !((ent.getBoundingBox().maxY + ent.getBoundingBox().minY) / 2D < pos.getY() - 1);
            case DOWN:
                return !((ent.getBoundingBox().maxY + ent.getBoundingBox().minY) / 2D > pos.getY() + 2);
            case NORTH:
                return !(ent.posX > pos.getX() + 2);
            case SOUTH:
                return !(ent.posX < pos.getX() - 1);
            case EAST:
                return !(ent.posZ < pos.getZ() - 1);
            case WEST:
                return !(ent.posZ > pos.getZ() + 2);
        }
        return false;
    }
}
