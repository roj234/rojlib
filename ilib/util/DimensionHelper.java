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

import ilib.ImpLib;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class DimensionHelper {
    public static boolean unloadWorld(int id) {
        WorldServer w = DimensionManager.getWorld(id);

        if (w != null) {
            if (!w.playerEntities.isEmpty()) {
                return false;
            }

            try {
                w.saveAllChunks(true, null);
            } catch (MinecraftException var9) {
                ImpLib.logger().error("Caught an exception while saving all chunks:", var9);
            } finally {
                MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(w));
                w.flush();
                DimensionManager.setWorld(id, null, w.getMinecraftServer());
            }

            if (DimensionManager.isDimensionRegistered(id)) {
                DimensionManager.unregisterDimension(id);
                //return true;
            }// else {
            //   return false;
            //}

            return true;
        }
        return false;
    }

    public static int idFor(World world) {
        if (world == null) {
            throw new IllegalArgumentException("Cannot fetch the Dimension-ID from a null world!");
        } else if (world.provider == null) {
            Integer[] it = DimensionManager.getIDs();
            for (Integer i : it) {
                if (DimensionManager.getWorld(i) == world) {
                    return i;
                }
            }

            throw new RuntimeException("Unable to determine the dimension of world: " + world);
        } else {
            return world.provider.getDimension();
        }
    }

    /**
     * Get a world for a dimension, possibly loading it from the configuration manager.
     */
    public static World getWorldForDimension(World world, int dimId) {
        return getWorldForDimension(world, dimId, true);
    }

    public static World getWorldForDimension(World world, int dimId, boolean load) {
        if (world == null) {
            world = DimensionManager.getWorld(dimId);
            if (world != null) return world;
            if (load) {
                try {
                    DimensionManager.initDimension(dimId);
                } catch (Exception ignored) {
                }
                world = DimensionManager.getWorld(dimId);
                return world;
            }
            return null;
        }
        return world.getMinecraftServer().getWorld(dimId);
    }
}
