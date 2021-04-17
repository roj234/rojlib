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
package ilib.client.renderer.mirror.render.world;

import ilib.Config;
import ilib.client.renderer.mirror.portal.Portal;
import ilib.client.renderer.mirror.render.world.chunk.MyRenderChunk;
import ilib.client.util.DisplayListRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import roj.asm.nixim.Copy;
import roj.collect.MyHashMap;
import roj.math.MathUtils;

import javax.annotation.Nullable;
import java.util.*;

public class RenderGlobalProxy extends RenderGlobal {
    static final WeakHashMap<Entity, DisplayListRenderer[]> renders = Config.betterRenderGlobal ? new WeakHashMap<>() : null;
    static final IRenderChunkFactory listFactory = new ListChunkFactory();
    static final IRenderChunkFactory vboFactory = new VboChunkFactory();

    public Map<Portal, ViewFrustum> usedViewFrustums = new MyHashMap<>();
    public Deque<ViewFrustum> freeViewFrustums = new LinkedList<>();

    public float playerPrevYaw, playerPrevPitch,
            playerYaw, playerPitch,
            playerPrevHeadYaw, playerHeadYaw;
    public double playerPosX, playerPosY, playerPosZ,
            playerPrevPosX, playerPrevPosY, playerPrevPosZ,
            playerLastTickX, playerLastTickY, playerLastTickZ;

    public boolean released;

    public RenderGlobalProxy(Minecraft mcIn) {
        super(mcIn);

        if (this.vboEnabled) {
            this.renderContainer = new VboRenderList();
            this.renderChunkFactory = vboFactory;
        } else {
            this.renderContainer = new RenderList();
            this.renderChunkFactory = listFactory;
        }
    }

    @Override
    public void loadRenderers() {
        if (this.world != null) {
            if (this.renderDispatcher == null) {
                this.renderDispatcher = new ChunkRenderDispatcher();
            }

            this.displayListEntitiesDirty = true;
            Blocks.LEAVES.setGraphicsLevel(this.mc.gameSettings.fancyGraphics);
            Blocks.LEAVES2.setGraphicsLevel(this.mc.gameSettings.fancyGraphics);
            this.renderDistanceChunks = this.mc.gameSettings.renderDistanceChunks;
            boolean flag = this.vboEnabled;
            this.vboEnabled = OpenGlHelper.useVbo();

            if (flag && !this.vboEnabled) {
                this.renderContainer = new RenderList();
                this.renderChunkFactory = listFactory;
            } else if (!flag && this.vboEnabled) {
                this.renderContainer = new VboRenderList();
                this.renderChunkFactory = vboFactory;
            }

            if (flag != this.vboEnabled) {
                this.generateStars();
                this.generateSky();
                this.generateSky2();
            }

            cleanViewFrustums();

            this.stopChunkUpdates();

            synchronized (this.setTileEntities) {
                this.setTileEntities.clear();
            }

            this.renderEntitiesStartupCounter = 2;
        }
    }

    public void cleanViewFrustums() {
        for (ViewFrustum e : usedViewFrustums.values()) {
            e.deleteGlResources();
            if (e == viewFrustum) {
                viewFrustum = null;
            }
        }
        usedViewFrustums.clear();
        for (ViewFrustum frustum : freeViewFrustums) {
            frustum.deleteGlResources();
            if (frustum == viewFrustum) {
                viewFrustum = null;
            }
        }
        freeViewFrustums.clear();
        if (this.viewFrustum != null) {
            this.viewFrustum.deleteGlResources();
        }
        freeViewFrustums.add(viewFrustum = new ViewFrustum(this.world, this.mc.gameSettings.renderDistanceChunks, this, this.renderChunkFactory));
    }

    public void releaseViewFrustum(Portal pm) {
        ViewFrustum vf = usedViewFrustums.remove(pm);
        if (vf != null) {
            freeViewFrustums.add(vf);
        }
    }

    public void bindViewFrustum(Portal pm) {
        ViewFrustum vf = usedViewFrustums.get(pm);
        if (vf == null) {
            if (freeViewFrustums.isEmpty()) {
                vf = new ViewFrustum(this.world, this.mc.gameSettings.renderDistanceChunks, this, this.renderChunkFactory);
            } else {
                vf = freeViewFrustums.removeLast();
            }
            usedViewFrustums.put(pm, vf);

            if (this.world != null) {
                Entity entity = this.mc.getRenderViewEntity();

                if (entity != null) {
                    vf.updateChunkPositions(entity.posX, entity.posZ);
                    for (RenderChunk renderChunk : vf.renderChunks) {
                        renderChunk.setNeedsUpdate(false);
                    }
                }
            }
        }
        viewFrustum = vf;
        for (RenderChunk renderChunk : viewFrustum.renderChunks) {
            //            renderChunk.setNeedsUpdate(false);
            if (renderChunk instanceof MyRenderChunk) {
                ((MyRenderChunk) renderChunk).setCurrentPositionsAndFaces(pm.getPos(), pm.getFaceOn());
                if (pm.getPair() != null) {
                    ((MyRenderChunk) renderChunk).setNoCull(!pm.getPair().getCullRender());
                }
            }
        }
    }

    public void storePlayerInfo() {
        playerPrevYaw = mc.player.prevRotationYaw;
        playerPrevPitch = mc.player.prevRotationPitch;
        playerYaw = mc.player.rotationYaw;
        playerPitch = mc.player.rotationPitch;
        playerPrevHeadYaw = mc.player.prevRotationYawHead;
        playerHeadYaw = mc.player.rotationYawHead;
        playerPosX = mc.player.posX;
        playerPosY = mc.player.posY;
        playerPosZ = mc.player.posZ;
        playerPrevPosX = mc.player.prevPosX;
        playerPrevPosY = mc.player.prevPosY;
        playerPrevPosZ = mc.player.prevPosZ;
        playerLastTickX = mc.player.lastTickPosX;
        playerLastTickY = mc.player.lastTickPosY;
        playerLastTickZ = mc.player.lastTickPosZ;
    }

    @Override
    public void renderEntities(Entity renderViewEntity, ICamera camera, float partialTicks) {
        renderEntities(renderViewEntity, camera, partialTicks, null);
    }

    public boolean shouldRenderEntity(Entity ent, Portal portal) {
        if (portal.getPair() == null)
            return false;
        AxisAlignedBB box = ent.getEntityBoundingBox();
        AxisAlignedBB plane = portal.getFlatPlane();
        switch (portal.getFaceOn()) {
            case UP:
                return (box.maxY + box.minY) / 2D < plane.minY;
            case DOWN:
                return (box.maxY + box.minY) / 2D > plane.minY;
            case NORTH:
                return box.minZ > plane.minZ;
            case SOUTH:
                return box.maxZ < plane.minZ;
            case EAST:
                return box.maxX < plane.minX;
            case WEST:
                return box.minX > plane.minX;
        }
        return false;
    }

    @Override
    public void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean updateImmediately) {
        for (ViewFrustum e : usedViewFrustums.values()) {
            e.markBlocksForUpdate(minX, minY, minZ, maxX, maxY, maxZ, updateImmediately);
        }
        for (ViewFrustum frustum : freeViewFrustums) {
            frustum.markBlocksForUpdate(minX, minY, minZ, maxX, maxY, maxZ, updateImmediately);
        }
    }

    @Override
    public void playRecord(@Nullable SoundEvent soundIn, BlockPos pos) {
    }

    @Override
    public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent soundIn, SoundCategory category, double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {
    }

    public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data) {
    }

    public void renderEntities(Entity view, ICamera camera, float partialTicks, Portal portal) {
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

            double ex = self.lastTickPosX + (self.posX - self.lastTickPosX) * (double) partialTicks;
            double ey = self.lastTickPosY + (self.posY - self.lastTickPosY) * (double) partialTicks;
            double ez = self.lastTickPosZ + (self.posZ - self.lastTickPosZ) * (double) partialTicks;

            this.renderManager.setRenderPosition(TileEntityRendererDispatcher.staticPlayerX = ex, TileEntityRendererDispatcher.staticPlayerY = ey, TileEntityRendererDispatcher.staticPlayerZ = ez);

            this.mc.entityRenderer.enableLightmap();

            if (Config.betterRenderGlobal) {
                DisplayListRenderer[] renderers = renders.computeIfAbsent(view, (e) -> new DisplayListRenderer[]{new DisplayListRenderer(true), new DisplayListRenderer(false)});

                if (Config.entityUpdateFreq > 1) {
                    DisplayListRenderer r = renderers[0];
                    if (r.needUpdate()) {
                        r.start(view, partialTicks);

                        render__Entities(camera, partialTicks, pass, self, view, portal, ex, ey, ez);

                        r.end();
                    } else {
                        this.world.profiler.endStartSection("batch_entity");
                        r.draw(view, partialTicks);
                    }
                } else {
                    render__Entities(camera, partialTicks, pass, self, view, portal, ex, ey, ez);
                }


                if (Config.tileUpdateFreq > 1) {
                    DisplayListRenderer r = renderers[1];
                    if (r.needUpdate()) {
                        r.start(view, partialTicks);

                        renderTESR(this, camera, partialTicks, pass);

                        r.end();
                    } else {
                        this.world.profiler.endStartSection("batch_tesr");
                        r.draw(view, partialTicks);
                    }
                } else {
                    renderTESR(this, camera, partialTicks, pass);
                }
            } else {
                render__Entities(camera, partialTicks, pass, self, view, portal, ex, ey, ez);
                renderTESR(this, camera, partialTicks, pass);
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

    public void render__Entities(ICamera camera, float partialTicks, int pass, Entity self, Entity viewing, @Nullable Portal from, double ex, double ey, double ez) {
        this.world.profiler.endStartSection("global");
        if (pass == 0) {
            this.countEntitiesTotal = this.world.getLoadedEntityList().size();
        }

        final double dx = MathUtils.interpolate(viewing.prevPosX, viewing.posX, partialTicks);
        final double dy = MathUtils.interpolate(viewing.prevPosY, viewing.posY, partialTicks);
        final double dz = MathUtils.interpolate(viewing.prevPosZ, viewing.posZ, partialTicks);

        List<Entity> weatherEffects = this.world.weatherEffects;
        for (int i = 0; i < weatherEffects.size(); ++i) {
            Entity eff = weatherEffects.get(i);
            if (eff.shouldRenderInPass(pass)) {
                ++this.countEntitiesRendered;
                if (eff.isInRangeToRender3d(dx, dy, dz)) {
                    this.renderManager.renderEntityStatic(eff, partialTicks, false);
                }
            }
        }

        EntityPlayerSP player = mc.player;

        float prevYaw = player.prevRotationYaw;
        float prevPitch = player.prevRotationPitch;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float prevYawHead = player.prevRotationYawHead;
        float yawHead = player.rotationYawHead;
        double posX = player.posX;
        double posY = player.posY;
        double posZ = player.posZ;
        double prevPosX = player.prevPosX;
        double prevPosY = player.prevPosY;
        double prevPosZ = player.prevPosZ;
        double lastX = player.lastTickPosX;
        double lastY = player.lastTickPosY;
        double lastZ = player.lastTickPosZ;

        player.prevRotationYaw = playerPrevYaw;
        player.prevRotationPitch = playerPrevPitch;
        player.rotationYaw = playerYaw;
        player.rotationPitch = playerPitch;
        player.prevRotationYawHead = playerPrevHeadYaw;
        player.rotationYawHead = playerHeadYaw;
        player.posX = playerPosX;
        player.posY = playerPosY;
        player.posZ = playerPosZ;
        player.prevPosX = playerPrevPosX;
        player.prevPosY = playerPrevPosY;
        player.prevPosZ = playerPrevPosZ;
        player.lastTickPosX = playerLastTickX;
        player.lastTickPosY = playerLastTickY;
        player.lastTickPosZ = playerLastTickZ;

        this.world.profiler.endStartSection("entities");

        boolean doOutline = pass == 0 && this.isRenderEntityOutlines();

        List<Entity> outline = doOutline ? new ArrayList<>() : null;
        List<Entity> multipass = new ArrayList<>();


        boolean hasPortal = from != null && from.getPair() != null;
        Portal dest = hasPortal ? from.getPair() : null;

        boolean renderSelf = this.mc.gameSettings.thirdPersonView != 0 || (viewing instanceof EntityLivingBase && ((EntityLivingBase) viewing).isPlayerSleeping());

        BlockPos.PooledMutableBlockPos mPos = BlockPos.PooledMutableBlockPos.retain();
        for (ContainerLocalRenderInformation info : this.renderInfos) {
            BlockPos pos = info.renderChunk.getPosition();

            Chunk chunk = this.world.getChunk(pos);
            ClassInheritanceMultiMap<Entity> entities = chunk.getEntityLists()[pos.getY() >> 4];

            if (entities.isEmpty()) continue;

            for (Entity entity : entities) {

                if (hasPortal && !(entity == player && mc.gameSettings.thirdPersonView == 0) && from.lastScanEntities.contains(entity) && from.getPortalInsides(entity).intersects(entity.getEntityBoundingBox())) {
                    final double eePosX = MathUtils.interpolate(viewing.lastTickPosX, viewing.posX, partialTicks);
                    final double eePosY = MathUtils.interpolate(viewing.lastTickPosY, viewing.posY, partialTicks);
                    final double eePosZ = MathUtils.interpolate(viewing.lastTickPosZ, viewing.posZ, partialTicks);

                    AxisAlignedBB flatPlane = from.getFlatPlane();
                    double centerX = (flatPlane.maxX + flatPlane.minX) / 2D;
                    double centerY = (flatPlane.maxY + flatPlane.minY) / 2D;
                    double centerZ = (flatPlane.maxZ + flatPlane.minZ) / 2D;

                    AxisAlignedBB pairFlatPlane = from.getPair().getFlatPlane();
                    double destX = (pairFlatPlane.maxX + pairFlatPlane.minX) / 2D;
                    double destY = (pairFlatPlane.maxY + pairFlatPlane.minY) / 2D;
                    double destZ = (pairFlatPlane.maxZ + pairFlatPlane.minZ) / 2D;

                    GlStateManager.pushMatrix();

                    double rotX = eePosX - ex;
                    double rotY = eePosY - ey;
                    double rotZ = eePosZ - ez;

                    float[] off = from.getFormula().calcPosRot(new float[]{
                            (float) (eePosX - centerX),
                            (float) (eePosY - centerY),
                            (float) (eePosZ - centerZ)
                    });

                    float[] rot = from.getFormula().calcRotRot(new float[]{
                            180F,
                            0F,
                            0F
                    });

                    GlStateManager.translate(destX - eePosX + off[0], destY - eePosY + off[1], destZ - eePosZ + off[2]);
                    GlStateManager.translate(rotX, rotY, rotZ);

                    GlStateManager.rotate(-rot[0], 0F, 1F, 0F);
                    GlStateManager.rotate(-rot[1], 1F, 0F, 0F);
                    GlStateManager.rotate(-rot[2], 0F, 0F, 1F);

                    GlStateManager.translate(-(rotX), -(rotY), -(rotZ));

                    this.renderManager.renderEntityStatic(entity, partialTicks, false);

                    GlStateManager.popMatrix();
                }

                if ((dest == null || shouldRenderEntity(entity, dest)) && entity.shouldRenderInPass(pass)) {
                    if (this.renderManager.shouldRender(entity, camera, dx, dy, dz) || entity.isRidingOrBeingRiddenBy(this.mc.player)) {
                        if (entity != self || renderSelf) {
                            if (entity.posY < 0 || entity.posY >= 256 || world.isBlockLoaded(mPos.setPos(entity))) {
                                ++this.countEntitiesRendered;

                                boolean noStencil = dest != null && dest.lastScanEntities.contains(entity) && dest.box.intersects(entity.getEntityBoundingBox());

                                if (noStencil) {
                                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                                }
                                this.renderManager.renderEntityStatic(entity, partialTicks, false);
                                if (noStencil) {
                                    GL11.glEnable(GL11.GL_STENCIL_TEST);
                                }

                                if (doOutline && this.isOutlineActive(entity, self, camera)) {
                                    outline.add(entity);
                                }

                                if (this.renderManager.isRenderMultipass(entity)) {
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
                this.renderManager.renderMultipass(multipass.get(i), partialTicks);
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
                this.renderManager.setRenderOutlines(true);

                for (int j = 0; j < outline.size(); ++j) {
                    this.renderManager.renderEntityStatic(outline.get(j), partialTicks, false);
                }

                this.renderManager.setRenderOutlines(false);
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

        player.prevRotationYaw = prevYaw;
        player.prevRotationPitch = prevPitch;
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        player.prevRotationYawHead = prevYawHead;
        player.rotationYawHead = yawHead;
        player.posX = posX;
        player.posY = posY;
        player.posZ = posZ;
        player.prevPosX = prevPosX;
        player.prevPosY = prevPosY;
        player.prevPosZ = prevPosZ;
        player.lastTickPosX = lastX;
        player.lastTickPosY = lastY;
        player.lastTickPosZ = lastZ;
    }

    @Copy
    public static void renderTESR(RenderGlobal rg, ICamera camera, float partialTicks, int pass) {
        rg.world.profiler.endStartSection("blockentities");

        RenderHelper.enableStandardItemLighting();
        TileEntityRendererDispatcher.instance.preDrawBatch();

        for (ContainerLocalRenderInformation info : rg.renderInfos) {
            List<TileEntity> tiles = info.renderChunk.getCompiledChunk().getTileEntities();
            if (tiles.isEmpty()) continue;
            for (TileEntity tile : tiles) {
                if (tile.shouldRenderInPass(pass) && camera.isBoundingBoxInFrustum(tile.getRenderBoundingBox())) {
                    TileEntityRendererDispatcher.instance.render(tile, partialTicks, -1);
                }
            }
        }

        synchronized (rg.setTileEntities) {
            for (TileEntity tile : rg.setTileEntities) {

                if (tile.shouldRenderInPass(pass) && camera.isBoundingBoxInFrustum(tile.getRenderBoundingBox())) {
                    TileEntityRendererDispatcher.instance.render(tile, partialTicks, -1);
                }
            }
        }

        TileEntityRendererDispatcher.instance.drawBatch(pass);
    }
}