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

package ilib.world.saver;

import ilib.ATHandler;
import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import roj.concurrent.pool.TaskExecutor;
import roj.concurrent.task.ITaskNaCl;

import java.io.File;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/31 21:56
 */
public final class WorldSaver {
    static boolean enable = false;
    static int sid = 0;
    static boolean enter = false;

    public static boolean isClientServer() {
        return ClientProxy.mc.getIntegratedServer() != null;
    }

    static TaskExecutor executor = new TaskExecutor();

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if(!enable) return;
        World w = event.getWorld();
        if (!w.isRemote) return;
        if ((w instanceof WorldClient) && !isClientServer()) {
            try {
                ChunkSavingProvider savingProvider = (ChunkSavingProvider) w.getChunkProvider();
                savingProvider.saveWorld();
            } catch (Throwable e) {
                PlayerUtil.sendTo(null, "尾巴保存失败: " + e);
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        sid++;
        if(!enable) return;
        enter = true;
        World w = event.getWorld();
        if (!w.isRemote) return;
        if ((w instanceof WorldClient) && !isClientServer()) {
            ImpLib.logger().debug("Injecting world " + w.provider.getDimension());
            // debug
            try {
                StringBuilder sb = new StringBuilder("saves/mi_download_").append(sid).append("/");
                int dim = DimensionHelper.idFor(w);
                if (dim != 0) {
                    sb.append("DIM").append(dim).append("/");
                }
                File worldPath = new File(sb.toString());
                WriteOnlySaveHandler handler = new WriteOnlySaveHandler(worldPath, w.getWorldInfo());
                ChunkSavingProvider savingProvider = new ChunkSavingProvider(w, worldPath);
                ilib.ATHandler.setChunkProvider(w, savingProvider);
                ilib.ATHandler.setClientChunkProvider((WorldClient) w, savingProvider);
                ATHandler.setMapStorage(w, new MapStorage(handler));
                //ImpLib.logger().info("Success injected client world with ChunkSavingProvider");
                //TimeUtil.beginText.add("1s后保存方块ID数据!请不要退出，除非你的MOD和服务器 **完全** 相同!");
                executor.pushTask(new BlockIdSaver(handler, w.getWorldInfo()));
            } catch (Throwable e) {
                PlayerUtil.sendTo(null, "世界注入失败: " + e);
                throw new RuntimeException("Failure during injecting ChunkSavingProvider", e);
            }
        }
    }

    public static boolean toggleEnable() {
        enable = !enable;
        if(enable && executor.getState() == Thread.State.NEW) {
            executor.start();
        }
        return enable;
    }

    public static void plusSid() {
        if(enter) {
            sid++;
            enter = false;
        }
    }

    public static class BlockIdSaver implements Runnable, ITaskNaCl {
        public BlockIdSaver(WriteOnlySaveHandler handler, WorldInfo info) {
            this.h = handler;
            this.i = info;
        }

        private final WriteOnlySaveHandler h;
        private final WorldInfo i;

        public void run() {
            NBTTagCompound tag = new NBTTagCompound();
            net.minecraftforge.fml.common.FMLCommonHandler.instance().handleWorldDataSave(h, i, tag);
            h.saveData0(i, new NBTTagCompound(), tag);
            PlayerUtil.sendTo(null, "方块ID数据已经保存!");
        }

        @Override
        public void calculate(Thread thread) {
            try {
                Thread.sleep(1000);
            } catch (Exception ignored) {}
            Minecraft.getMinecraft().addScheduledTask(this);
        }

        @Override
        public boolean isDone() {
            return false;
        }
    }

    static {
        MinecraftForge.EVENT_BUS.register(WorldSaver.class);
    }
}
