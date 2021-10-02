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

package ilib.util.energy;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.api.energy.IMEnergy;
import ilib.capabilities.Capabilities;
import ilib.client.renderer.ArenaRenderer;
import ilib.client.renderer.WaypointRenderer;
import ilib.math.FastPath;
import ilib.math.Section;
import ilib.util.DimensionHelper;
import ilib.util.ItemUtils;
import ilib.util.PlayerUtil;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.math.MathUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class EnergyTransferProcessor implements Runnable {
    private static final Set<TileEntity> energyTiles = new MyHashSet<>();

    private static final FastPath<Network.Primer> loading = new FastPath<>();

    private static int nextNetworkId = 0;
    private static final IntMap<Network> available = new IntMap<>();
    private static final FastPath<Network> fastPath = new FastPath<>();

    private static final String THREAD_NAME = "ME Network";
    private static final EnergyTransferProcessor instance = new EnergyTransferProcessor();
    private static Thread worker;

    private static final AtomicInteger lock = new AtomicInteger();

    public static final int NOT_JOIN_OR_LEFT = -9999;
    public static final int NO_ME = -7233;

    @SubscribeEvent
    public static void onRenderPos(RenderWorldLastEvent event) {
        enqueueLock();
        for (IntMap.Entry<Network> ent : available.entrySet()) {
            BlockPos[] mm = ent.getValue().aabb.minMaxBlock();
            ArenaRenderer.INSTANCE.render(mm[0], mm[1], event.getPartialTicks(), true);
        }
        releaseLock();
    }

    @SubscribeEvent
    public static void onChunkSaveData(ChunkDataEvent.Save event) {
        if (1 == 1) return;
        long off = ((long) (event.getChunk().x) << 42) | event.getChunk().z;

        int world = DimensionHelper.idFor(event.getWorld());

        List<Network> networks = new LinkedList<>();
        for (int i = 0; i < 16; i++) {
            long off1 = off | ((long) i << 21);
            networks.addAll(fastPath.getByShifted(world, off1));
        }
        if (networks.isEmpty())
            return;

        PlayerUtil.broadcastAll("保存网络: " + networks);

        NBTTagList list = new NBTTagList();

        Network.Primer pn = new Network.Primer();

        enqueueLock();
        for (Network network : networks) {
            list.appendTag(new NBTTagByteArray(network.write()));
            available.remove(network.id);
            fastPath.remove(network);

            pn.network = network;
            loading.remove(pn);
        }
        releaseLock();

        event.getData().setTag("MENet", list);
    }

    @SubscribeEvent
    public static void onChunkLoadData(ChunkDataEvent.Load event) {
        NBTTagCompound tag = event.getData();
        if (tag.hasKey("MENet", 9)) {
            List<Network.Primer> loaded = new LinkedList<>();
            NBTTagList tagList = tag.getTagList("MENet", 7);
            for (int i = 0; i < tagList.tagCount(); i++) {
                try {
                    Network.Primer net = Network.readFrom(((NBTTagByteArray) tagList.get(i)).getByteArray());
                    PlayerUtil.broadcastAll("加载网络: " + net.network);

                    loading.put(net);

                    final IChunkProvider provider = event.getWorld().getChunkProvider();
                    for (PrimitiveIterator.OfLong itr = net.chunks.keySet().iterator(); itr.hasNext(); ) {
                        long chunkPos = itr.nextLong();
                        if (provider.getLoadedChunk((int) (chunkPos >>> 32), (int) chunkPos) != null) {
                            itr.remove();
                        }
                    }
                    if (net.chunks.isEmpty()) {
                        loaded.add(net);
                        loading.remove(net);
                    }
                } catch (Throwable e) {
                    ImpLib.logger().error("网络加载失败! ");
                    ImpLib.logger().catching(e);
                }
            }
            if (!loaded.isEmpty()) {
                enqueueLock();
                for (Network.Primer network : loaded) {
                    PlayerUtil.broadcastAll("A加载完成的网络: " + network.network);
                    registerNetwork(network.network);
                }
                releaseLock();
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        long off = ((long) (event.getChunk().x) << 42) | event.getChunk().z;

        int world = DimensionHelper.idFor(event.getWorld());

        List<Network.Primer> loaded = new LinkedList<>();
        for (int i = 0; i < 16; i++) {
            long off1 = off | ((long) i << 21);
            for (Network.Primer network : loading.getByShifted(world, off1)) {
                if (network.onChunkLoad(event.getWorld(), event.getChunk())) {
                    loaded.add(network);
                    loading.remove(network);
                }
            }
        }

        enqueueLock();
        for (Network.Primer network : loaded) {
            PlayerUtil.broadcastAll("加载完成的网络: " + network.network);
            System.err.println("加载完成的网络: " + network.network);
            registerNetwork(network.network);
        }
        releaseLock();
    }


    private static void registerNetwork(Network network) {
        available.put(network.id, network);
        fastPath.put(network);
    }


    public static void burstIt(World world, BlockPos pos, int level) {
        ImpLib.proxy.runAtMainThread(false, () -> {
            ItemUtils.breakBlock(world, pos);
            world.newExplosion(null, pos.getX(), pos.getY(), pos.getZ(), level, true, true);
        });
    }


    public static void register(TileEntity te) {
        if (te == null || te.isInvalid()) {
            throw new IllegalArgumentException("Invalid or null TileEntity");
        }
        IMEnergy im = te.getCapability(Capabilities.MENERGY_TILE, null);
        if (im == null) {
            ImpLib.logger().warn("Not IMEnergy tile " + te.getClass().getName());
            return;
        }
        if (te.getWorld() == null) {
            throw new IllegalArgumentException("Illegal world!");
        }
        if (te.getWorld().isRemote) {
            throw new IllegalArgumentException("Client-side call");
        }

        enqueueLock();
        if (!energyTiles.add(te)) {
            throw new IllegalArgumentException("Register twice");
        }

        int world = DimensionHelper.idFor(te.getWorld());

        Network network = fastPath.get(world, te.getPos());
        if (network == null) {
            network = new Network(new Section(te.getPos(), te.getPos()), nextNetworkId++, world);
            PlayerUtil.broadcastAll("创建网络" + network);
            System.err.println("创建网络" + network);
            registerNetwork(network);
        }

        network.addEntry(te.getPos(), im);

        WaypointRenderer.addIfNotPresent("#" + network.id, "#" + network.id, te.getPos().getX(), te.getPos().getY(), te.getPos().getZ(), world, 0xFF0000);

        im.onJoin(network.id);
        releaseLock();
    }

    public static void unregister(TileEntity te) {
        IMEnergy im = te.getCapability(Capabilities.MENERGY_TILE, null);
        if (im == null) {
            ImpLib.logger().warn("Not IMEnergy tile " + te.getClass().getName());
            return;
        }
        if (te.getWorld() == null) {
            throw new IllegalArgumentException("Illegal world!");
        }
        if (te.getWorld().isRemote) {
            throw new IllegalArgumentException("Client-side call");
        }

        enqueueLock();
        if (!energyTiles.remove(te)) {
            throw new IllegalArgumentException("Not register");
        }

        Network network = fastPath.get(DimensionHelper.idFor(te.getWorld()), te.getPos());
        if (network != null) {
            network.removeEntry(te.getPos());
        }
        releaseLock();

        im.onLeave();
    }

    public static void startThread() {
        if (worker == null) {
            worker = new Thread(instance, THREAD_NAME);
            MinecraftForge.EVENT_BUS.register(EnergyTransferProcessor.class);
        }

        if (worker.getState() != Thread.State.NEW) {
            reset();
        }
        ImpLib.logger().info("Starting async energy transmitting thread");
        worker.start();
    }

    public static void reset() {
        if (worker.isAlive()) {
            worker.interrupt();
            try {
                worker.join(100L);
            } catch (InterruptedException ignored) {
            }
        }
        if (worker.getState() != Thread.State.NEW) {
            worker = new Thread(instance, THREAD_NAME);

            enqueueLock();
            energyTiles.clear();
            loading.clear();
            if (!available.isEmpty()) {
                System.err.println("Not empty: " + available);
            }
            available.clear();
            releaseLock();

            nextNetworkId = 0;

            ImpLib.logger().debug("[ME] Reset!");
        }
    }

    private EnergyTransferProcessor() {
    }

    public void run() {
        long nowTime, pastTime, elapsedTime = 0L;
        long currentTime = System.currentTimeMillis();

        Thread thread = Thread.currentThread();

        try {
            while (!thread.isInterrupted()) {
                nowTime = System.currentTimeMillis();
                pastTime = MathUtils.clamp(nowTime - currentTime, 0L, 2000L);

                currentTime = nowTime;
                elapsedTime += pastTime;

                if (!checkGamePaused())
                    while (elapsedTime > 50L) {
                        elapsedTime -= 50L;
                        tick();
                    }
                else elapsedTime = 0;
                Thread.sleep(Math.max(1L, 50L - elapsedTime));
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable thr) {
            ImpLib.logger().fatal("==================================================");
            ImpLib.logger().fatal("能源传输线程检测到无法处理的异常: ");
            ImpLib.logger().catching(Level.FATAL, thr);
            ImpLib.logger().fatal("==================================================");
            PlayerUtil.broadcastAll("能源传输线程检测到无法处理的异常: " + thr);
        }
    }

    private static boolean checkGamePaused() {
        if (!ImpLib.isClient) return false;
        return checkGamePaused0();
    }

    @SideOnly(Side.CLIENT)
    private static boolean checkGamePaused0() {
        return ClientProxy.mc.isGamePaused();
    }

    private static int tick;
    protected static final PathFinder pf = new WideFirstPathFinder();

    public static void tick() {
        if (++tick == 100) {
            tick = 0;

            int removed = 0;
            enqueueLock();
            for (TileEntity te : energyTiles) {
                if (te.isInvalid()) {
                    removed++;
                    unregister(te);
                    ImpLib.logger().warn("删除不存在的 " + te.getPos());
                }
            }
            releaseLock();

            if (removed > 0)
                ImpLib.logger().debug("清除了 " + removed + "个无效TE");
            if (WideFirstPathFinder.badEndCount > 0) {
                if (PlayerUtil.getOnlinePlayers().isEmpty())
                    ImpLib.logger().warn("5s内PathFind出现了 " + WideFirstPathFinder.badEndCount + " 次over length limit error!");
                else
                    PlayerUtil.broadcastAll("5s内PathFind出现了 " + WideFirstPathFinder.badEndCount + " 次过长路径!");
                WideFirstPathFinder.badEndCount = 0;
            }

            if (WideFirstPathFinder.timeCost > 1200) {
                if (PlayerUtil.getOnlinePlayers().isEmpty())
                    ImpLib.logger().info("5s内PathFind总用时 " + WideFirstPathFinder.timeCost);
                else
                    PlayerUtil.broadcastAll("寻路时间过长! 如果这个提示短时间出现10+次 你就没了");
                WideFirstPathFinder.increase();
            } else {
                WideFirstPathFinder.decrease();
            }
            WideFirstPathFinder.timeCost = 0;
        }

        enqueueLock();
        for (Iterator<Network> itr = available.values().iterator(); itr.hasNext(); ) {
            Network network = itr.next();
            if (network.isEmpty()) {
                PlayerUtil.broadcastAll("删除空网络 " + network);
                itr.remove();
            } else {
                network.tick(pf);
            }
        }
        releaseLock();
    }

    private static void releaseLock() {
        while (!lock.compareAndSet(1, 0)) {
            if(lock.get() == 0)
                return;
            Thread.yield();
        }
    }

    private static void enqueueLock() {
        while (!lock.compareAndSet(0, 1)) {
            Thread.yield();
        }
    }
}
