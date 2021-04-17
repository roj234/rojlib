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
package ilib.command.sub.we;

import ilib.util.BlockHelper;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import roj.concurrent.TaskExecutor;
import roj.concurrent.ThreadStateMonitor;
import roj.concurrent.task.CalculateTask;
import roj.text.TextUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/24 0:25
 */
public class ModificationCache implements ThreadStateMonitor {
    public static final LinkedList<ModificationInfo> undo = new LinkedList<>();
    public static final LinkedList<ModificationInfo> redo = new LinkedList<>();

    private static final ThreadStateMonitor INSTANCE = new ModificationCache();

    public static boolean enable = false, async = true;
    public static final AtomicInteger working = new AtomicInteger();

    public static int maxRedoCount = 20;
    public static int maxUndoCount = 20;

    private static TaskExecutor executorThread;

    public static void setEnable(boolean enable) {
        ModificationCache.enable = enable;
        if (!enable) {
            undo.clear();
            redo.clear();
        }
    }

    public static long getMemoryBytes() {
        long totalBytes = 4 + 1 + 4 + 4 + 4 + (4 + 4 * 6 + 4 + 4) + 4 + 24 + undo.size() * 4 + redo.size() * 4;
        for (ModificationInfo info : undo) {
            totalBytes += info.blockData.length * 4 + 4 + 4 * 6 + 4;
        }
        for (ModificationInfo info : redo) {
            totalBytes += info.blockData.length * 4 + 4 + 4 * 6 + 4;
        }
        return totalBytes;
    }

    public static int affectedBlocks = 0;

    public static boolean undo() {
        if (!enable || undo.isEmpty()) {
            return false;
        }
        ModificationInfo info = undo.pop().diff(redo);
        if (info != null) {
            redo.push(info);
            if (redo.size() > maxRedoCount) {
                redo.removeFirst();
            }
        }
        return true;
    }

    public static boolean redo() {
        if (!enable || redo.isEmpty()) {
            return false;
        }
        ModificationInfo info = redo.pop().diff(undo);
        if (info != null) {
            undo.push(info);
            if (undo.size() > maxRedoCount) {
                undo.removeFirst();
            }
        }
        return true;
    }

    public static boolean cancel(ModificationInfo info) {
        if (!enable)
            return true;
        if (undo.peek() == info) {
            undo.pop();
            return true;
        }
        return false;
    }

    private static void asyncRun(Callable<ModificationInfo> operate, LinkedList<ModificationInfo> pushTo) {
        if (executorThread == null || !executorThread.isAlive()) {
            executorThread = new TaskExecutor(INSTANCE);
            executorThread.start();
        }

        while (!working.compareAndSet(0, 1)) {
            Thread.yield();
        }

        final CalculateTask<ModificationInfo> task = new CalculateTask<>(operate);
        executorThread.pushTask(task);
        executorThread.execute(() -> {
            try {
                final ModificationInfo info = task.get();
                if (info != null) {
                    pushTo.push(info);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            working.set(0);
        });
    }

    /**
     * 在操作前调用！！！
     */
    public static ModificationInfo beforeOperate(World world, BlockPos start, BlockPos end) {
        if (!enable)
            return ModificationInfo.EMPTY;
        ModificationInfo info;
        undo.push(info = ModificationInfo.createSnapshot(world, start, end));
        if (undo.size() > maxRedoCount) {
            undo.removeFirst();
        }
        redo.clear();
        return info;
    }

    public static ModificationInfo beforeOperate(World world, BlockPos start, BlockPos end, BooleanSupplier operate) {
        if (!enable) {
            operate.getAsBoolean();
            return ModificationInfo.EMPTY;
        }
        redo.clear();
        return ModificationInfo.createSnapshot(world, start, end, operate);
    }

    public static int getAffectBlocks() {
        int a = affectedBlocks;
        affectedBlocks = 0;
        return a;
    }

    public static void setAsync(boolean async) {
        ModificationCache.async = async;
    }

    @Override
    public boolean threadDeath(TaskExecutor executor) {
        return !async;
    }

    @Override
    public boolean working() {
        return async;
    }

    public static class ModificationInfo {
        public static final ModificationInfo EMPTY = new ModificationInfo(0, 0, 0, 0, 0, 0, null);

        final int
                sX, sY, sZ,
                eX, eY, eZ;
        final World world;
        final IBlockState[] blockData;

        public ModificationInfo(int sX, int sY, int sZ, int eX, int eY, int eZ, World world) {
            this.sX = sX;
            this.sY = sY;
            this.sZ = sZ;
            this.eX = eX;
            this.eY = eY;
            this.eZ = eZ;
            this.world = world;
            this.blockData = new IBlockState[(eX - sX + 1) * (eY - sY + 1) * (eZ - sZ + 1)];
        }

        public static ModificationInfo createSnapshot(World world, BlockPos start, BlockPos end, BooleanSupplier runnable) {
            ModificationInfo result = new ModificationInfo(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), world);

            result.saveData(world, runnable);

            return result;
        }

        public static ModificationInfo createSnapshot(World world, BlockPos start, BlockPos end) {
            return createSnapshot(world, start, end, null);
        }

        @Override
        public String toString() {
            return "ModificationInfo{" +
                    "start=[" + sX +
                    ',' + sY +
                    ',' + sZ +
                    "], end=[" + eX +
                    ',' + eY +
                    ',' + eZ +
                    "], world=" + DimensionHelper.idFor(world) +
                    '}';
        }

        public void saveData(World world, BooleanSupplier runnable) {
            final int lZ = (eZ - sZ + 1);
            final int lYZ = (eY - sY + 1) * lZ;

            final BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

            final IBlockState[] saveTo = blockData;

            long time = System.currentTimeMillis();

            for (int x = sX, dx = 0; x < eX + 1; x++, dx++) {
                final int index1 = lYZ * dx;
                for (int y = sY, dy = 0; y < eY + 1; y++, dy++) {
                    final int index2 = index1 + lZ * dy;
                    for (int z = sZ, dz = 0; z < eZ + 1; z++, dz++) {
                        final int index3 = index2 + dz;
                        saveTo[index3] = world.getBlockState(pos.setPos(x, y, z));
                    }
                }
            }

            pos.release();

            if (runnable != null && !runnable.getAsBoolean()) {
                ModificationCache.cancel(this);
            }

            PlayerChunkMap map = ((WorldServer) world).getPlayerChunkMap();

            int x = sX >> 4;
            int z = sZ >> 4;
            for (; x < eX; x++) {
                for (; z < eZ; z++) {
                    PlayerChunkMapEntry entry = map.getEntry(x, z);
                    if (entry != null) {
                        entry.sendToPlayers();
                    }
                }
            }

            PlayerUtil.broadcastAll("操作耗时: " + TextUtil.getScaledNumber(System.currentTimeMillis() - time) + "ms");
        }

        public ModificationInfo operate(ModificationInfo result) {
            final IBlockState[] saveTo = result.blockData;
            final IBlockState[] readFrom = this.blockData;

            final int lZ = (eZ - sZ + 1);
            final int lYZ = (eY - sY + 1) * lZ;

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            IBlockState state;

            int affected = 0;

            long time = System.currentTimeMillis();

            for (int x = sX, dx = 0; x < eX + 1; x++, dx++) {
                final int index1 = lYZ * dx;
                for (int y = sY, dy = 0; y < eY + 1; y++, dy++) {
                    final int index2 = index1 + lZ * dy;
                    for (int z = sZ, dz = 0; z < eZ + 1; z++, dz++) {
                        final int index3 = index2 + dz;
                        saveTo[index3] = state = world.getBlockState(pos.setPos(x, y, z));
                        if (state != readFrom[index3]) {
                            affected++;
                            world.setBlockState(pos, readFrom[index3], BlockHelper.PLACEBLOCK_SENDCHANGE | BlockHelper.PLACEBLOCK_NO_OBSERVER);
                        }
                    }
                }
            }

            PlayerUtil.broadcastAll("操作耗时: " + TextUtil.getScaledNumber(System.currentTimeMillis() - time) + "ms");

            affectedBlocks += affected;

            return result;
        }

        public ModificationInfo diff(LinkedList<ModificationInfo> pushTo) {
            final ModificationInfo result = new ModificationInfo(sX, sY, sZ, eX, eY, eZ, world);
            if (async) {
                asyncRun(() -> operate(result), pushTo);
                return null;
            }
            return operate(result);
        }
    }
}
