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
package ilib.client.util;

import ilib.ClientProxy;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.GameSettings.Options;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.INpc;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/10/20 0:57
 */
public class MyDebugOverlay {
    interface Helper {
        int getDebugFPS();

        PriorityBlockingQueue<ChunkCompileTaskGenerator> getUpdate(ChunkRenderDispatcher dispatcher);
        BlockingQueue<RegionRenderCacheBuilder> getFree(ChunkRenderDispatcher dispatcher);
        Queue<?> getUpload(ChunkRenderDispatcher dispatcher);
        List<Thread> getWorkers(ChunkRenderDispatcher dispatcher);

        SoundManager getSoundManager(SoundHandler handler);
        Map<String, ISound> getPlaying(SoundManager sm);
    }
    static Helper h = DirectAccessor.builder(Helper.class)
            .delegate(Minecraft.class, "field_71470_ab", "getDebugFPS")
            .access(ChunkRenderDispatcher.class, new String[] {
                    "field_178519_d", "field_178520_e", "field_178524_h", "field_178522_c"
            }, new String[]{
                    "getUpdate", "getFree", "getUpload", "getWorkers"
            }, null)
            .access(SoundHandler.class, "field_147694_f", "getSoundManager", null)
            .access(SoundManager.class, "field_148629_h", "getPlaying", null)
            .build();

    public static void process(ArrayList<String> left, ArrayList<String> right) {
        Minecraft mc = ClientProxy.mc;
        GameSettings set = mc.gameSettings;

        left.ensureCapacity(20);
        left.add("\u00a76水雷艇 1.14.514 \u00a7r(" + mc.getVersion() + "/" + /*ClientBrandRetriever.getClientModName()*/"锻造锤" + ("release".equalsIgnoreCase(mc.getVersionType()) ? "" :
                "/" + mc.getVersionType()) + ")");
        left.add("\u00a7f" + h.getDebugFPS() + " / " +
                 (set.limitFramerate == Options.FRAMERATE_LIMIT.getValueMax() ? "\u00a7c无限制" : set.limitFramerate) + " \u00a7eFPS  " +
                 "\u00a76" + RenderChunk.renderChunksUpdated + " \u00a7e区块更新  " +
                 "\u00a76模式:" + (set.enableVsync ? " \u00a7a垂直同步" : "") +
                         (set.fancyGraphics ? "" : " \u00a7b流畅") +
                         (set.clouds == 1 ? " \u00a7c流畅的云" : "") +
                         (OpenGlHelper.useVbo() ? " \u00a7dVBO" : ""));
        IntegratedServer s = (IntegratedServer) PlayerUtil.getMinecraftServer();
        left.add("\u00a76渲染距离: \u00a7a" + set.renderDistanceChunks + "  " + (s == null ? "" : "\u00a7a本地服务器TPS: \u00a7b" + 50 / (MathHelper.average(s.tickTimeArray) * 1.0E-6D)));
        left.add("");

        EntityPlayerMP serverMe = s == null ? null : s.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        Entity me = mc.getRenderViewEntity();
        left.add("\u00a7aX\u00a7bY\u00a7cZ: \u00a7a" + me.posX + "  \u00a7b" + me.posY + "  \u00a7c" + me.posZ);
        int intX, intY, intZ;
        left.add("\u00a7a方块: \u00a7a" + (intX = MathHelper.floor(me.posX)) + "  \u00a7b" + (intY = MathHelper.floor(me.posY)) + "  \u00a7c" + (intZ = MathHelper.floor(me.posZ)));
        left.add("\u00a7a区块: \u00a7a" + (intX >> 4) + "  \u00a7b" + (intY >> 4) + "  \u00a7c" + (intZ >> 4) + " \u00a78相对 \u00a7a" + (intX & 15) + "  \u00a7b" + (intY & 15) + "  \u00a7c" + (intZ & 15));
        left.add("");

        left.add("\u00a79区块: \u00a7a正在渲染\u00a7f / \u00a7e等待渲染\u00a7f / \u00a7e等待上传VBO\u00a7f / \u00a7e合计  \u00a7b空闲的渲染器");
        RenderGlobal rg = mc.renderGlobal;
        left.add("\u00a79渲染: \u00a7a" + getRenderedChunks(rg) + " \u00a7f/ " +
                         "\u00a7e" + h.getUpdate(rg.renderDispatcher).size() + " \u00a7f/ " +
                         "\u00a7e" + h.getUpload(rg.renderDispatcher).size() + " \u00a7f/ " +
                         "\u00a7e" + rg.viewFrustum.renderChunks.length + "  " +
                         "\u00a7b" + h.getFree(rg.renderDispatcher).size() + "  " +
                         (h.getWorkers(rg.renderDispatcher).isEmpty() ? "\u00a7c单线程模式" : ""));
        left.add(s == null ? " ~~~ " : "\u00a79服务器加载的区块: \u00a7c" + ((WorldServer) serverMe.world).getChunkProvider().loadedChunks.size());
        left.add("\u00a79光照更新: \u00a7a" + rg.setLightUpdates.size() + "  \u00a7b全局TESR: " + rg.setTileEntities.size());
        left.add("");

        EnumFacing face = me.getHorizontalFacing();
        left.add("\u00a75维度: \u00a7b" + DimensionType.getById(DimensionHelper.idFor(me.world)).getName() +
                         "\u00a7e(\u00a7a" + DimensionHelper.idFor(me.world) + "\u00a7e)");
        left.add("\u00a75方向: \u00a7b" + face.getName() +
                         "\u00a7e(\u00a7a" + (face.getAxisDirection().getOffset() > 0 ? "正 " : "负") + face.getAxis().name() + "\u00a7e)");
        left.add("\u00a75旋转角: \u00a7b" + me.rotationYaw +
                         " \u00a75俯仰角: \u00a7b" + me.rotationPitch);
        if (intY < 0 || intY > 255) {
            left.add("\u00a65世界之外");
        } else {
            Chunk IamAt = mc.world.getChunk(intX >> 4, intZ >> 4);
            if (IamAt.isEmpty()) {
                left.add("\u00a75等待区块数据");
            } else {
                ExtendedBlockStorage storage = IamAt.getBlockStorageArray()[intY >> 4];
                if (storage != Chunk.NULL_BLOCK_STORAGE) {
                    left.add("\u00a75亮度: \u00a7a方块/天空  \u00a7b" +storage.getBlockLight(intX, intY, intZ) + "/" +
                                     (storage.getSkyLight() == null ? "无" : storage.getSkyLight(intX, intY, intZ)));
                    BlockPos pos = me.getPosition();
                    left.add("\u00a5生物群系: \u00a7b" + mc.world.getBiome(pos).getRegistryName());

                    DifficultyInstance difficulty = mc.world.getDifficultyForLocation(pos);
                    if (serverMe != null) {
                        difficulty = serverMe.world.getDifficultyForLocation(serverMe.getPosition());
                    }

                    left.add("\u00a75本地难度: \u00a7b" + difficulty.getAdditionalDifficulty() + "  限制: " + difficulty.getClampedAdditionalDifficulty());
                } else {
                    left.add("\u00a75无数据");
                }
            }
            if (serverMe != null) {
                Chunk serverAt = serverMe.world.getChunk(intX >> 4, intZ >> 4);
                ExtendedBlockStorage storage = serverAt.getBlockStorageArray()[intY >> 4];
                left.add("\u00a75亮度(服务器): \u00a7a方块/天空  \u00a7b" + storage.getBlockLight(intX, intY, intZ) + "/" +
                                 (storage.getSkyLight() == null ? "无" : storage.getSkyLight(intX, intY, intZ)));
                BlockPos pos = serverMe.getPosition();
                left.add("\u00a75生物群系(服务器): \u00a7b" + serverMe.world.getBiome(pos).getRegistryName());

                DifficultyInstance difficulty = serverMe.world.getDifficultyForLocation(pos);
                left.add("\u00a75本地难度(服务器): \u00a7b" + difficulty.getAdditionalDifficulty() + "  限制: " + difficulty.getClampedAdditionalDifficulty());
            }
        }
        left.add("\u00a75游戏时长: \u00a7b" + (mc.world.getWorldTime() / 24000L));
        if (mc.entityRenderer != null && mc.entityRenderer.isShaderActive()) {
            left.add("\u00a75激活的光影: " + mc.entityRenderer.getShaderGroup().getShaderGroupName());
        }
        left.add("");

        left.add("\u00a7c存在: \u00a7e粒子效果/实体/怪物/动物/村民/玩家/挂的/无生命 | \u00a7c" + mc.effectRenderer.getStatistics() + "/" + mc.world.getLoadedEntityList().size() + filterData(mc.world.getLoadedEntityList()));
        if (s != null)
            left.add("\u00a7c存在(服务器): \u00a7e实体/怪物/动物/村民/玩家/挂的/无生命 | \u00a7c" + serverMe.world.getLoadedEntityList().size() + filterData(serverMe.world.getLoadedEntityList()));
        SoundManager sm = h.getSoundManager(mc.getSoundHandler());
        Map<String, ISound> playingSounds = h.getPlaying(sm);
        left.add("\u00a77播放的声音: " + playingSounds.size());


        left.add("");
        left.add("\u00a7e扩展信息: 耗时饼图 [F3+shift]: " + (set.showDebugProfilerChart ? "\u00a7a开" : "\u00a7c关") + " TPS [F3+alt]: " + (set.showLagometer ? "\u00a7a开" : "\u00a7c关"));
        left.add("\u00a7e更多信息: \u00a7b[F3 + Q]");

        right.ensureCapacity(20);
        right.add("\u00a76 " + (mc.isJava64bit() ? "64" : "32") + "位Java " + System.getProperty("java.version"));

        long max = Runtime.getRuntime().maxMemory();
        long total = Runtime.getRuntime().totalMemory();
        long used = total - Runtime.getRuntime().freeMemory();
        right.add("\u00a76内存: \u00a6b" + (used * 100L / max) + "% " + (used >> 20) + "/" + (max >> 20) + "MB");
        right.add("\u00a76已分配: \u00a7c" + (total * 100L / max) + "% " + (total >> 20) + "MB");
        right.add("");
        right.add("\u00a76CPU: \u00a7b" + OpenGlHelper.getCpu());
        right.add("\u00a76尺寸: \u00a7b" + Display.getWidth() + "x" + Display.getHeight());
        right.add("\u00a76显卡: \u00a7b" + GL11.glGetString(GL11.GL_RENDER));
        right.add("\u00a76GL版本: \u00a7b" + GL11.glGetString(GL11.GL_VERSION));
        right.add("");
        right.addAll(FMLCommonHandler.instance().getBrandings(false));
        RayTraceResult rsl = mc.objectMouseOver;
        if (rsl != null && rsl.typeOfHit == Type.BLOCK && rsl.getBlockPos() != null) {
            BlockPos pos = rsl.getBlockPos();
            IBlockState state = mc.world.getBlockState(pos);
            if (mc.world.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                state = state.getActualState(mc.world, pos);
            }

            right.add("");
            right.add("\u00a79目标方块: \u00a7a" + pos.getX() + " \u00a7b" + pos.getY() + " \u00a7c" + pos.getZ() + " \u00a7r" + Block.REGISTRY.getNameForObject(state.getBlock()));

            String n1;
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                Object t = entry.getValue();
                if (Boolean.TRUE.equals(t)) {
                    n1 = TextFormatting.GREEN + "true";
                } else if (Boolean.FALSE.equals(t)) {
                    n1 = TextFormatting.RED + "false";
                } else {
                    n1 = "\u00a7e" + entry.getKey().getName(Helpers.cast(t));
                }
                right.add("\u00a77" + entry.getKey().getName() + ": " + n1);
            }
        }
        Vec3d start = me.getPositionVector();
        Vec3d look = me.getLookVec();
        rsl = mc.world.rayTraceBlocks(start, start.add(look.x * 5, look.y * 5, look.z * 5), true);
        x:
        if (rsl != null && rsl.typeOfHit == Type.BLOCK && rsl.getBlockPos() != null) {
            BlockPos pos = rsl.getBlockPos();
            IBlockState state = mc.world.getBlockState(pos);
            if (mc.world.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                state = state.getActualState(mc.world, pos);
            }
            if (!(state.getBlock() instanceof IFluidBlock)) {
                break x;
            }

            right.add("");
            right.add("\u00a79目标液体: \u00a7a" + pos.getX() + " \u00a7b" + pos.getY() + " \u00a7c" + pos.getZ() + " \u00a7r" + Block.REGISTRY.getNameForObject(state.getBlock()));

            String n1;
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                Object t = entry.getValue();
                if (Boolean.TRUE.equals(t)) {
                    n1 = TextFormatting.GREEN + "true";
                } else if (Boolean.FALSE.equals(t)) {
                    n1 = TextFormatting.RED + "false";
                } else {
                    n1 = "\u00a7e" + entry.getKey().getName(Helpers.cast(t));
                }
                right.add("\u00a77" + entry.getKey().getName() + ": " + n1);
            }
        }
    }

    private static String filterData(List<Entity> list) {
        int mst=0,anm=0,vil=0,ply=0,nlf=0,pnt=0;
        for (int i = 0; i < list.size(); i++) {
            Entity e = list.get(i);
            if (e instanceof EntityPlayer) {
                ply++;
            } else if (e instanceof IMob) {
                mst++;
            } else if (e instanceof INpc) {
                vil++;
            } else if (e instanceof IAnimals) {
                anm++;
            } else if (e instanceof EntityHanging) {
                pnt++;
            } else if (!(e instanceof EntityLivingBase)) {
                nlf++;
            }
        }
        return "/" + mst + "/" + anm + "/" + vil + "/" + ply + "/" + pnt + "/" + nlf;
    }

    protected static int getRenderedChunks(RenderGlobal RG) {
        int i = 0;
        Iterator<RenderGlobal.ContainerLocalRenderInformation> itr = RG.renderInfos.iterator();
        while (itr.hasNext()) {
            RenderGlobal.ContainerLocalRenderInformation info = itr.next();
            CompiledChunk compiledchunk = info.renderChunk.compiledChunk;
            if (compiledchunk != CompiledChunk.DUMMY && !compiledchunk.isEmpty()) {
                ++i;
            }
        }

        return i;
    }
}
