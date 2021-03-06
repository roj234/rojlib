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
package ilib.client.mirror.render.world;

import ilib.ImpLib;
import ilib.asm.util.MCReplaces;
import ilib.client.mirror.Portal;
import org.lwjgl.opengl.GL11;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.math.MathUtils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RenderGlobalProxy extends RenderGlobal {
    static final IRenderChunkFactory listFactory = CulledListChunk::new;
    static final IRenderChunkFactory vboFactory = CulledVboChunk::new;

    private final Map<Portal, MyViewFrustum> usingVFs = new MyHashMap<>();
    private final List<MyViewFrustum>        freeVFs  = new SimpleList<>();

    public RenderGlobalProxy(Minecraft mc) {
        super(mc);
        renderChunkFactory = OpenGlHelper.useVbo() ? vboFactory : listFactory;
        setTileEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Override
    public final void generateSky() {
        if (skyVBO != null) {
            skyVBO.deleteGlBuffers();
            skyVBO = null;
        } else if (glSkyList >= 0) {
            GL11.glDeleteLists(glSkyList, 1);
            glSkyList = -1;
        }
    }
    @Override
    public final void generateSky2() {
        if (sky2VBO != null) {
            sky2VBO.deleteGlBuffers();
            sky2VBO = null;
        } else if (glSkyList2 >= 0) {
            GL11.glDeleteLists(glSkyList2, 1);
            glSkyList2 = -1;
        }
    }
    @Override
    public final void generateStars() {
        if (starVBO != null) {
            starVBO.deleteGlBuffers();
            starVBO = null;
        } else if (starGLCallList >= 0) {
            GL11.glDeleteLists(starGLCallList, 1);
            starGLCallList = -1;
        }
    }

    @Override
    public void updateTileEntities(Collection<TileEntity> remove, Collection<TileEntity> add) {
        this.setTileEntities.removeAll(remove);
        this.setTileEntities.addAll(add);
    }

    @Override
    public void loadRenderers() {
        if (world != null) {
            if (renderDispatcher == null) {
                renderDispatcher = new ChunkRenderDispatcher(2);
            }

            RenderGlobal rg = mc.renderGlobal;
            starVBO = rg.starVBO;
            skyVBO = rg.skyVBO;
            sky2VBO = rg.sky2VBO;
            starGLCallList = rg.starGLCallList;
            glSkyList = rg.glSkyList;
            glSkyList2 = rg.glSkyList2;

            boolean prevVbo = vboEnabled;
            vboEnabled = OpenGlHelper.useVbo();

            if (prevVbo && !vboEnabled) {
                renderContainer = new RenderList();
                renderChunkFactory = listFactory;
            } else if (!prevVbo && vboEnabled) {
                renderContainer = new VboRenderList();
                renderChunkFactory = vboFactory;
            }

            displayListEntitiesDirty = true;
            renderDistanceChunks = mc.gameSettings.renderDistanceChunks;

            cleanViewFrustums();

            stopChunkUpdates();

            setTileEntities.clear();

            renderEntitiesStartupCounter = 2;
        }
    }

    @Override
    public void setWorldAndLoadRenderers(WorldClient newWorld) {
        if (world == newWorld) return;

        if (world != null) {
            world.removeEventListener(this);
        }

        // renderManager.setWorld(newWorld);
        world = newWorld;
        if (newWorld != null) {
            newWorld.addEventListener(this);
            loadRenderers();
        } else {
            chunksToUpdate.clear();
            renderInfos.clear();
            cleanViewFrustums();

            if (renderDispatcher != null) {
                renderDispatcher.stopWorkerThreads();
            }
            renderDispatcher = null;
        }
    }

    public void cleanViewFrustums() {
        for (MyViewFrustum e : usingVFs.values()) {
            e.deleteGlResources();
            if (e == viewFrustum) {
                viewFrustum = null;
            }
        }
        usingVFs.clear();

        for (int i = 0; i < freeVFs.size(); i++) {
            MyViewFrustum f = freeVFs.get(i);
            f.deleteGlResources();
            if (f == viewFrustum) {
                viewFrustum = null;
            }
        }
        freeVFs.clear();

        if (viewFrustum != null) {
            viewFrustum.deleteGlResources();
        }

        MyViewFrustum mvf = new MyViewFrustum(world, renderDistanceChunks, this, renderChunkFactory);
        freeVFs.add(mvf);
        viewFrustum = mvf;
    }

    public void releaseViewFrustum(Portal pm) {
        MyViewFrustum vf = usingVFs.remove(pm);
        if (vf != null) {
            if (vf == viewFrustum)
                viewFrustum = null;

            if(freeVFs.size() < 8) {
                freeVFs.add(vf);
            } else {
                vf.deleteGlResources();
            }
        }
    }

    public void bindViewFrustum(Portal p) {
        MyViewFrustum vf = usingVFs.get(p);
        if (vf == null) {
            findvf:
            if (freeVFs.isEmpty()) {
                long time = System.currentTimeMillis();
                for (Iterator<MyViewFrustum> itr = usingVFs.values().iterator(); itr.hasNext(); ) {
                    MyViewFrustum vf1 = itr.next();
                    if (time - vf1.lastActive > 60000) {
                        vf = vf1;
                        itr.remove();
                        break findvf;
                    }
                }

                vf = new MyViewFrustum(world, renderDistanceChunks, this, renderChunkFactory);
            } else {
                vf = freeVFs.remove(freeVFs.size() - 1);
            }
            usingVFs.put(p, vf);
        }

        frustumUpdatePosX = vf.frustumUpdatePosX;
        frustumUpdatePosY = vf.frustumUpdatePosY;
        frustumUpdatePosZ = vf.frustumUpdatePosZ;
        frustumUpdatePosChunkX = vf.frustumUpdatePosChunkX;
        frustumUpdatePosChunkY = vf.frustumUpdatePosChunkY;
        frustumUpdatePosChunkZ = vf.frustumUpdatePosChunkZ;

        // ???????????? Step1: ?????????????????????????????????????????????
        CulledVboChunk.CullFunc fn = null;
        BlockPos pos = p.getPos();
        switch (p.getFaceOn()) {
            case NORTH:
                fn = (pos1, state) -> pos1.getZ() > pos.getZ();
                break;
            case SOUTH:
                fn = (pos1, state) -> pos1.getZ() < pos.getZ();
                break;
            case WEST:
                fn = (pos1, state) -> pos1.getX() > pos.getX();
                break;
            case EAST:
                fn = (pos1, state) -> pos1.getX() < pos.getX();
                break;
            case UP:
                fn = (pos1, state) -> pos1.getY() < pos.getY();
                break;
            case DOWN:
                fn = (pos1, state) -> pos1.getY() > pos.getY();
                break;
        }

        RenderChunk[] chunks = vf.renderChunks;
        if (chunks.length > 0 && !(chunks[0] instanceof CulledVboChunk)) {
            ImpLib.logger().error("Some mod replaced RenderGlobalProxy.chunkRenderFactory");
            return;
        }

        // noinspection all
        for (int i = 0; i < chunks.length; i++) {
            CulledChunk rc = (CulledChunk) chunks[i];

            if (p.getPair() != null) {
                boolean cull = p.getPair().getCullRender();
                if (!cull) {
                    rc.setCullFunc(null);
                    continue;
                }
            }
            rc.setCullFunc(fn);
        }

        viewFrustum = vf;
    }

    public void unbindViewFrustum() {
        MyViewFrustum vf = (MyViewFrustum) viewFrustum;
        vf.frustumUpdatePosX = frustumUpdatePosX;
        vf.frustumUpdatePosY = frustumUpdatePosY;
        vf.frustumUpdatePosZ = frustumUpdatePosZ;
        vf.frustumUpdatePosChunkX = frustumUpdatePosChunkX;
        vf.frustumUpdatePosChunkY = frustumUpdatePosChunkY;
        vf.frustumUpdatePosChunkZ = frustumUpdatePosChunkZ;
    }

    public Set<EnumFacing> getVisibleFacings(BlockPos pos) {
        MyVisGraph vg = MCReplaces.get().graph;
        vg.clear();

        BlockPos p = new BlockPos(pos.getX() >> 4 << 4, pos.getY() >> 4 << 4, pos.getZ() >> 4 << 4);
        Chunk chunk = this.world.getChunk(p);
        for (BlockPos pos1 : BlockPos.getAllInBoxMutable(p, p.add(15, 15, 15))) {
            if (chunk.getBlockState(pos1).isOpaqueCube()) {
                vg.setOpaqueCube(pos1);
            }
        }

        return vg.getVisibleFacings(pos);
    }

    public float playerPrevYaw, playerPrevPitch,
            playerYaw, playerPitch,
            playerPrevHeadYaw, playerHeadYaw;
    public double playerPosX, playerPosY, playerPosZ,
            playerPrevPosX, playerPrevPosY, playerPrevPosZ,
            playerLastTickX, playerLastTickY, playerLastTickZ;

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
        if (portal.getPair() == null) return false;
        AxisAlignedBB box = ent.getEntityBoundingBox();
        AxisAlignedBB plane = portal.getPlane();
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

    public void renderEntities(Entity view, ICamera camera, float partialTicks, Portal portal) {
        int pass = MinecraftForgeClient.getRenderPass();
        if (renderEntitiesStartupCounter > 0) {
            if (pass <= 0) --renderEntitiesStartupCounter;
        } else {
            world.profiler.startSection("prepare");

            Entity rve = mc.getRenderViewEntity();

            TileEntityRendererDispatcher.instance.prepare(world, mc.getTextureManager(), mc.fontRenderer, rve, mc.objectMouseOver, partialTicks);

            this.renderManager.cacheActiveRenderInfo(world, mc.fontRenderer, rve, mc.pointedEntity, mc.gameSettings, partialTicks);

            if (pass == 0) {
                this.countEntitiesTotal = 0;
                this.countEntitiesRendered = 0;
                this.countEntitiesHidden = 0;
            }

            double ex = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * (double) partialTicks;
            double ey = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * (double) partialTicks;
            double ez = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * (double) partialTicks;

            this.renderManager.setRenderPosition(TileEntityRendererDispatcher.staticPlayerX = ex, TileEntityRendererDispatcher.staticPlayerY = ey, TileEntityRendererDispatcher.staticPlayerZ = ez);

            this.mc.entityRenderer.enableLightmap();

            render__Entities(camera, partialTicks, pass, rve, view, portal);
            renderTESR(this, camera, partialTicks, pass);

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

    public void render__Entities(ICamera camera, float partialTicks, int pass, Entity self, Entity viewing, @Nullable Portal from) {
        this.world.profiler.endStartSection("global");
        if (pass == 0) countEntitiesTotal = world.getLoadedEntityList().size();

        double dx = MathUtils.interpolate(viewing.prevPosX, viewing.posX, partialTicks);
        double dy = MathUtils.interpolate(viewing.prevPosY, viewing.posY, partialTicks);
        double dz = MathUtils.interpolate(viewing.prevPosZ, viewing.posZ, partialTicks);

        RenderManager rm = this.renderManager;

        List<Entity> weatherEffects = this.world.weatherEffects;
        for (int i = 0; i < weatherEffects.size(); ++i) {
            Entity eff = weatherEffects.get(i);
            if (eff.shouldRenderInPass(pass)) {
                ++countEntitiesRendered;
                if (eff.isInRangeToRender3d(dx, dy, dz)) {
                    rm.renderEntityStatic(eff, partialTicks, false);
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

        Portal dest = from != null ? from.getPair() : null;

        boolean renderSelf = this.mc.gameSettings.thirdPersonView != 0 || (viewing instanceof EntityLivingBase && ((EntityLivingBase) viewing).isPlayerSleeping());

        EntityPlayerSP p = this.mc.player;

        BlockPos.PooledMutableBlockPos mPos = BlockPos.PooledMutableBlockPos.retain();
        List<ContainerLocalRenderInformation> infos = this.renderInfos;
        for (int i = 0; i < infos.size(); i++) {
            BlockPos pos = infos.get(i).renderChunk.getPosition();
            ClassInheritanceMultiMap<Entity> entities = world.getChunk(pos).getEntityLists()[pos.getY() >> 4];

            if (entities.isEmpty()) continue;

            for (Entity entity : entities) {
                if (entity.shouldRenderInPass(pass)) {
                    if (rm.shouldRender(entity, camera, dx, dy, dz) || entity.isRidingOrBeingRiddenBy(p)) {
                        if (entity != self || renderSelf) {
                            if (entity.posY < 0 || entity.posY >= 256 || world.isBlockLoaded(mPos.setPos(entity))) {
                                ++countEntitiesRendered;

                                rm.renderEntityStatic(entity, partialTicks, false);
                                if (doOutline && isOutlineActive(entity, self, camera)) {
                                    outline.add(entity);
                                }

                                if (rm.isRenderMultipass(entity)) {
                                    multipass.add(entity);
                                }
                            }
                        }
                    }
                }
            }
        }

        mPos.release();

        for (int i = 0; i < multipass.size(); i++) {
            rm.renderMultipass(multipass.get(i), partialTicks);
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
                rm.setRenderOutlines(true);

                for (int j = 0; j < outline.size(); ++j) {
                    rm.renderEntityStatic(outline.get(j), partialTicks, false);
                }

                rm.setRenderOutlines(false);
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

    public static void renderTESR(RenderGlobal rg, ICamera camera, float partialTicks, int pass) {
        rg.world.profiler.endStartSection("blockentities");

        RenderHelper.enableStandardItemLighting();
        TileEntityRendererDispatcher.instance.preDrawBatch();

        List<ContainerLocalRenderInformation> infos = rg.renderInfos;
        for (int j = 0; j < infos.size(); j++) {
            List<TileEntity> tiles = infos.get(j).renderChunk.getCompiledChunk().getTileEntities();
            for (int i = 0; i < tiles.size(); i++) {
                TileEntity tile = tiles.get(i);
                if (tile.shouldRenderInPass(pass) && camera.isBoundingBoxInFrustum(tile.getRenderBoundingBox())) {
                    TileEntityRendererDispatcher.instance.render(tile, partialTicks, -1);
                }
            }
        }

        if (rg instanceof RenderGlobalProxy) {
            doGlobalTESR(rg, camera, partialTicks, pass);
        } else {
            synchronized (rg.setTileEntities) {
                doGlobalTESR(rg, camera, partialTicks, pass);
            }
        }

        TileEntityRendererDispatcher.instance.drawBatch(pass);
    }

    private static void doGlobalTESR(RenderGlobal rg, ICamera camera, float partialTicks, int pass) {
        for (TileEntity tile : rg.setTileEntities) {
            if (tile.shouldRenderInPass(pass) && camera.isBoundingBoxInFrustum(tile.getRenderBoundingBox())) {
                TileEntityRendererDispatcher.instance.render(tile, partialTicks, -1);
            }
        }
    }

    @Override
    public void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean updateImmediately) {
        for (ViewFrustum e : usingVFs.values()) {
            e.markBlocksForUpdate(minX, minY, minZ, maxX, maxY, maxZ, updateImmediately);
        }
        for (int i = 0; i < freeVFs.size(); i++) {
            freeVFs.get(i).markBlocksForUpdate(minX, minY, minZ, maxX, maxY, maxZ, updateImmediately);
        }
        System.err.println("mark for update");
    }

    @Override
    public final void playRecord(SoundEvent a, BlockPos b) {}

    @Override
    public final void playSoundToAllNearExcept(EntityPlayer a, SoundEvent b, SoundCategory c, double x, double y, double z, float d, float e) {}

    @Override
    public final void broadcastSound(int a, BlockPos b, int c) {}

    @Override
    public final void playEvent(EntityPlayer a, int b, BlockPos c, int d) {}
}