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
import roj.concurrent.TaskSequencer;
import roj.concurrent.task.ITask;

import java.io.File;

/**
 * @author Roj234
 * @since 2021/1/31 21:56
 */
public final class WorldSaver {
    static boolean enable = false;
    static int sid = 0;
    static boolean enter = false;

    public static boolean isClientServer() {
        return ClientProxy.mc.getIntegratedServer() != null;
    }

    static TaskSequencer executor = new TaskSequencer();
    static Thread seqThread = new Thread(executor);

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if(!enable) return;
        World w = event.getWorld();
        if (!w.isRemote) return;
        if ((w instanceof WorldClient) && !isClientServer() && w.chunkProvider instanceof ChunkSavingProvider) {
            try {
                ChunkSavingProvider savingProvider = (ChunkSavingProvider) w.chunkProvider;
                savingProvider.saveWorld();
                Thread.sleep(200);
            } catch (Throwable e) {
                PlayerUtil.sendTo(null, "??????????????????: " + e);
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        if(!enable) return;
        onWorldLoad(event.getWorld());
    }

    public static void onWorldLoad(World w) {
        if (!w.isRemote) return;
        if ((w instanceof WorldClient) && !isClientServer()) {
            ImpLib.logger().debug("Injecting world " + w.provider.getDimension());
            // debug
            try {
                StringBuilder sb = new StringBuilder("saves/????????????_").append(sid).append("/");
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
                executor.register(new BlockIdSaver(handler, w.getWorldInfo()), 0, 1000, 1);
                enter = true;
            } catch (Throwable e) {
                PlayerUtil.sendTo(null, "??????????????????: " + e);
                throw new RuntimeException("Failure during injecting ChunkSavingProvider", e);
            }
        }
    }

    private static void _changeState() {
        if (enable) {
            if (seqThread.getState() == Thread.State.NEW) {
                seqThread.setDaemon(true);
                seqThread.setName("WorldSaver Timer");
                seqThread.start();
                TagGetter.register();
            }
            WorldClient w = Minecraft.getMinecraft().world;
            if (w != null) onWorldLoad(w);
        }
    }

    public static boolean toggleEnable() {
        enable = !enable;
        _changeState();
        return enable;
    }

    public static void setEnable(boolean enable1) {
        enable = enable1;
        _changeState();
    }

    public static boolean isEnabled() {
        return enable;
    }

    public static void plusSid() {
        if(enter) {
            sid++;
            enter = false;
        }
    }

    public static class BlockIdSaver implements Runnable, ITask {
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
            PlayerUtil.sendTo(null, "??????ID??????????????????!");
        }

        @Override
        public void calculate() {
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
