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
package ilib.util;

import roj.collect.LongMap;
import roj.collect.MyHashSet;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.Set;

public final class PortalCache {
    private final LongMap<Set<BlockPos>> map = new LongMap<>();

    private PortalCache() {
    }

    public static final PortalCache OVERWORLD_CACHE = new PortalCache();
    public static final PortalCache NETHER_CACHE = new PortalCache();

    static {
        MinecraftForge.EVENT_BUS.register(PortalCache.class);
    }

    @SubscribeEvent
    public static void onPortalBlockEvent(BlockEvent e) {
        final BlockPos pos = e.getPos();
        final World world = e.getWorld();
        if (world instanceof WorldServer && pos.getY() < world.provider.getActualHeight()) {
            PortalCache handler;
            switch (DimensionHelper.idFor(world)) {
                case -1:
                    handler = NETHER_CACHE;
                    break;
                case 0:
                    handler = OVERWORLD_CACHE;
                    break;
                default:
                    return;

            }
            if (handler.isMarked(e.getPos())) {
                if (e.getState().getBlock() == Blocks.PORTAL) {
                    handler.addPortal(pos);
                } else {
                    handler.removePortal(pos);
                }
            }
        }
    }

    public static void removeStalePortalLocations(long l) {
        if (l == 1) { // per 5 mins

        }
    }

    public boolean addPortal(BlockPos pos) {
        long key = ((long) pos.getX() >>> 4) << 32 | pos.getZ() >>> 4;
        Set<BlockPos> set = map.get(key);
        if (set == null)
            return false;
        set.add(pos.toImmutable());
        return true;
    }

    public boolean markChunk(BlockPos pos) {
        long key = ((long) pos.getX() >>> 4) << 32 | pos.getZ() >>> 4;
        if (!map.containsKey(key)) {
            map.put(key, new MyHashSet<>());
            return true;
        }
        return false;
    }

    public boolean removePortal(BlockPos pos) {
        long key = ((long) pos.getX() >>> 4) << 32 | pos.getZ() >>> 4;
        Set<BlockPos> set = map.get(key);
        return set != null && set.remove(pos.toImmutable());
    }

    public boolean isMarked(BlockPos pos) {
        long key = ((long) pos.getX() >>> 4) << 32 | pos.getZ() >>> 4;
        return map.containsKey(key);
    }

    public Iterable<BlockPos> getChunkPortalIterable(BlockPos pos) {
        long key = ((long) pos.getX() >>> 4) << 32 | pos.getZ() >>> 4;
        Set<BlockPos> set = map.get(key);
        return set == null ? Collections.emptyList() : set;
    }

    public void clear() {
        map.clear();
    }
}
