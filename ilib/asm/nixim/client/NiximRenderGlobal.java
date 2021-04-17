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

import ilib.Config;
import ilib.client.renderer.mirror.render.world.RenderGlobalProxy;
import ilib.client.util.DisplayListRenderer;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.math.MathUtils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.client.MinecraftForgeClient;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/24 13:05
 */
@Nixim("net.minecraft.client.renderer.RenderGlobal")
public class NiximRenderGlobal extends RenderGlobal {
    @Copy
    static WeakHashMap<Entity, DisplayListRenderer[]> renders = new WeakHashMap<>();

    public NiximRenderGlobal(Minecraft mcIn) {
        super(mcIn);
    }

    @Inject("func_180446_a")
    @Override
    public void renderEntities(Entity view, ICamera camera, float partialTicks) {
        int pass = MinecraftForgeClient.getRenderPass();
        if (this.renderEntitiesStartupCounter > 0) {
            if (pass <= 0) {
                --this.renderEntitiesStartupCounter;
            }
        } else {
            this.world.profiler.startSection("prepare");

            Entity self = this.mc.getRenderViewEntity();

            TileEntityRendererDispatcher.instance.prepare(this.world, this.mc.getTextureManager(), this.mc.fontRenderer, self, this.mc.objectMouseOver, partialTicks);

            this.renderManager.cacheActiveRenderInfo(this.world, this.mc.fontRenderer, self, this.mc.pointedEntity, this.mc.gameSettings, partialTicks);

            if (pass == 0) {
                this.countEntitiesTotal = 0;
                this.countEntitiesRendered = 0;
                this.countEntitiesHidden = 0;
            }

            double ex = MathUtils.interpolate(self.lastTickPosX, self.posX, partialTicks);
            double ey = MathUtils.interpolate(self.lastTickPosY, self.posY, partialTicks);
            double ez = MathUtils.interpolate(self.lastTickPosZ, self.posZ, partialTicks);

            this.renderManager.setRenderPosition(TileEntityRendererDispatcher.staticPlayerX = ex, TileEntityRendererDispatcher.staticPlayerY = ey, TileEntityRendererDispatcher.staticPlayerZ = ez);

            this.mc.entityRenderer.enableLightmap();

            if (Config.betterRenderGlobal) {
                if (renders == null)
                    renders = new WeakHashMap<>();

                DisplayListRenderer[] renderers = renders.computeIfAbsent(view, (e) -> new DisplayListRenderer[]{new DisplayListRenderer(true), new DisplayListRenderer(false)});

                if (Config.entityUpdateFreq > 1) {
                    DisplayListRenderer r = renderers[0];
                    if (r.needUpdate()) {
                        r.start(view, partialTicks);

                        renderEntities(camera, partialTicks, pass, self, view);

                        r.end();
                    } else {
                        this.world.profiler.endStartSection("batch_entity");
                        r.draw(view, partialTicks);
                    }
                } else {
                    renderEntities(camera, partialTicks, pass, self, view);
                }


                if (Config.tileUpdateFreq > 1) {
                    DisplayListRenderer r = renderers[1];
                    if (r.needUpdate()) {
                        r.start(view, partialTicks);

                        RenderGlobalProxy.renderTESR(this, camera, partialTicks, pass);

                        r.end();
                    } else {
                        this.world.profiler.endStartSection("batch_tesr");
                        r.draw(view, partialTicks);
                    }
                } else {
                    RenderGlobalProxy.renderTESR(this, camera, partialTicks, pass);
                }
                this.world.profiler.endStartSection("blockentities");
            } else {
                renderEntities(camera, partialTicks, pass, self, view);
                RenderGlobalProxy.renderTESR(this, camera, partialTicks, pass);
            }

            this.preRenderDamagedBlocks();

            for (DestroyBlockProgress progress : this.damagedBlocks.values()) {
                BlockPos pos = progress.getPosition();
                TileEntity tile = this.world.getTileEntity(pos);
                if (tile != null) {
                    if (tile instanceof TileEntityChest) {
                        TileEntityChest chest = (TileEntityChest) tile;
                        if (chest.adjacentChestXNeg != null) {
                            pos = pos.offset(EnumFacing.WEST);
                            tile = this.world.getTileEntity(pos);
                        } else if (chest.adjacentChestZNeg != null) {
                            pos = pos.offset(EnumFacing.NORTH);
                            tile = this.world.getTileEntity(pos);
                        }
                    }

                    IBlockState state = this.world.getBlockState(pos);
                    if (tile != null && state.hasCustomBreakingProgress()) {
                        TileEntityRendererDispatcher.instance.render(tile,
                                partialTicks, progress.getPartialBlockDamage());
                    }
                }
            }

            this.postRenderDamagedBlocks();
            this.mc.entityRenderer.disableLightmap();
            this.mc.profiler.endSection();
        }
    }

    @Copy
    public void renderEntities(ICamera camera, float partialTicks, int pass, Entity self, Entity viewing) {
        this.world.profiler.endStartSection("global");
        if (pass == 0) {
            this.countEntitiesTotal = this.world.getLoadedEntityList().size();
        }

        double dx = MathUtils.interpolate(viewing.prevPosX, viewing.posX, partialTicks);
        double dy = MathUtils.interpolate(viewing.prevPosY, viewing.posY, partialTicks);
        double dz = MathUtils.interpolate(viewing.prevPosZ, viewing.posZ, partialTicks);

        RenderManager renderMan = this.renderManager;

        List<Entity> weatherEffects = this.world.weatherEffects;
        for (int i = 0; i < weatherEffects.size(); ++i) {
            Entity eff = weatherEffects.get(i);
            if (eff.shouldRenderInPass(pass)) {
                ++this.countEntitiesRendered;
                if (eff.isInRangeToRender3d(dx, dy, dz)) {
                    renderMan.renderEntityStatic(eff, partialTicks, false);
                }
            }
        }

        this.world.profiler.endStartSection("entities");

        boolean doOutline = pass == 0 && this.isRenderEntityOutlines();

        List<Entity> outline = doOutline ? new ArrayList<>() : null;
        List<Entity> multipass = new ArrayList<>();

        boolean renderSelf = this.mc.gameSettings.thirdPersonView != 0 || (viewing instanceof EntityLivingBase && ((EntityLivingBase) viewing).isPlayerSleeping());

        EntityPlayerSP player = this.mc.player;

        BlockPos.PooledMutableBlockPos mPos = BlockPos.PooledMutableBlockPos.retain();
        for (ContainerLocalRenderInformation info : this.renderInfos) {
            BlockPos chunkPos = info.renderChunk.getPosition();

            Chunk chunk = this.world.getChunk(chunkPos);
            ClassInheritanceMultiMap<Entity> entities = chunk.getEntityLists()[chunkPos.getY() >> 4];

            if (entities.isEmpty()) continue;

            for (Entity entity : entities) {
                if (entity.shouldRenderInPass(pass)) {
                    if (renderMan.shouldRender(entity, camera, dx, dy, dz) || entity.isRidingOrBeingRiddenBy(player)) {
                        if (entity != self || renderSelf) {
                            if (entity.posY < 0 || entity.posY >= 256 || world.isBlockLoaded(mPos.setPos(entity))) {
                                ++this.countEntitiesRendered;
                                renderMan.renderEntityStatic(entity, partialTicks, false);
                                if (doOutline && this.isOutlineActive(entity, self, camera)) {
                                    outline.add(entity);
                                }

                                if (renderMan.isRenderMultipass(entity)) {
                                    multipass.add(entity);
                                }
                            }
                        }
                    }
                }
            }
        }

        mPos.release();

        if (!multipass.isEmpty()) {
            for (int i = 0, size = multipass.size(); i < size; i++) {
                Entity entity1 = multipass.get(i);
                renderMan.renderMultipass(entity1, partialTicks);
            }
        }

        if (doOutline && (!outline.isEmpty() || this.entityOutlinesRendered)) {
            this.world.profiler.endStartSection("entityOutlines");
            this.entityOutlineFramebuffer.framebufferClear();
            this.entityOutlinesRendered = !outline.isEmpty();
            if (!outline.isEmpty()) {
                GlStateManager.depthFunc(519);
                GlStateManager.disableFog();
                this.entityOutlineFramebuffer.bindFramebuffer(false);
                RenderHelper.disableStandardItemLighting();
                renderMan.setRenderOutlines(true);

                for (int j = 0; j < outline.size(); ++j) {
                    renderMan.renderEntityStatic(outline.get(j), partialTicks, false);
                }

                renderMan.setRenderOutlines(false);
                RenderHelper.enableStandardItemLighting();
                GlStateManager.depthMask(false);
                this.entityOutlineShader.render(partialTicks);
                GlStateManager.enableLighting();
                GlStateManager.depthMask(true);
                GlStateManager.enableFog();
                GlStateManager.enableBlend();
                GlStateManager.enableColorMaterial();
                GlStateManager.depthFunc(515);
                GlStateManager.enableDepth();
                GlStateManager.enableAlpha();
            }

            this.mc.getFramebuffer().bindFramebuffer(false);
        }
    }
}
