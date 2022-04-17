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

package ilib.world.structure;

import ilib.ImpLib;
import ilib.util.BlockHelper;
import ilib.util.EntityHelper;
import ilib.world.schematic.Schematic;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Structure {
    public static final int F_REPLACE_AIR = 1, F_SPAWN_ENTITY = 2;

    protected Schematic schematic;

    public Structure(Schematic schematic) {
        this.schematic = schematic;
    }

    public void generate(World world, BlockPos loc, int flag) {
        generate(world, loc.getX(), loc.getY(), loc.getZ(), flag);
    }

    public void generate(World world, int xCoord, int yCoord, int zCoord, int flag) {
        if (world.isRemote) throw new IllegalStateException("world.isRemote");

        Schematic schem = this.getSchematic();

        BlockPos.PooledMutableBlockPos schematicPos = PooledMutableBlockPos.retain();
        BlockPos.PooledMutableBlockPos worldPos = PooledMutableBlockPos.retain();
        for (int x = 0; x < schem.width(); x++) {
            for (int y = 0; y < schem.height(); y++) {
                for (int z = 0; z < schem.length(); ++z) {
                    int worldX = xCoord + x;
                    int worldY = yCoord + y;
                    int worldZ = zCoord + z;

                    Block block = schem.getBlock(schematicPos.setPos(x, y, z));
                    IBlockState state = schem.getBlockState(schematicPos);
                    if (block != null) {
                        if (!block.isAir(state, world, worldPos.setPos(worldX, worldY, worldZ))) {
                            world.setBlockState(worldPos, state, 2);
                            if (block.hasTileEntity(state)) {
                                NBTTagCompound tag = schem.getTileData(x, y, z, worldX, worldY, worldZ);
                                if (tag != null) {
                                    TileEntity tile = world.getTileEntity(worldPos);
                                    if (tile == null) {
                                        ImpLib.logger().warn("Not a tile at " + worldX + ',' + worldY + ',' + worldZ + ", tag: " + tag);
                                        continue;
                                    }
                                    tile.readFromNBT(tag);
                                    BlockHelper.updateBlock(world, worldPos);
                                }
                            }
                        } else if ((flag & F_REPLACE_AIR) != 0 && !world.isAirBlock(worldPos)) {
                            world.setBlockState(worldPos, BlockHelper.AIR_STATE, 2);
                            world.removeTileEntity(worldPos);
                        }
                    }
                }
            }
        }

        schematicPos.release();
        worldPos.release();

        int chunkX = xCoord >> 4;
        int chunkZ = zCoord >> 4;
        for (int x = chunkX + (schem.width() >> 4); chunkX < x; chunkX++) {
            for (int y = chunkZ + (schem.length() >> 4); chunkZ < y; chunkZ++) {
                Chunk chunk = world.getChunk(chunkX, chunkZ);
                chunk.setModified(true);
                chunk.enqueueRelightChecks();
            }
        }

        if ((flag & F_SPAWN_ENTITY) != 0) {
            List<NBTTagCompound> entities = schem.getEntities();
            for (int i = 0; i < entities.size(); i++) {
                NBTTagCompound tag = entities.get(i);
                EntityHelper.spawnEntityFromTag(tag, world, tag.getInteger("x") + xCoord, tag.getInteger("y") + yCoord, tag.getInteger("z") + zCoord);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Structure> T setSchematic(Schematic schematic) {
        this.schematic = schematic;
        return (T) this;
    }

    public Schematic getSchematic() {
        return this.schematic;
    }
}
