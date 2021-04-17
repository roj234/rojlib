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
import ilib.world.structure.schematic.Schematic;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class AbstractStructure {
    protected Schematic schematic;

    public AbstractStructure(Schematic schematic) {
        this.schematic = schematic;
    }

    public final void generate(World world, int xCoord, int yCoord, int zCoord) {
        if (!world.isRemote) {
            Schematic schematic = this.getSchematic();

            int x;
            int y;
            BlockPos.MutableBlockPos schematicPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

            out:
            for (x = 0; x < schematic.width(); x++) {
                for (y = 0; y < schematic.height(); y++) {
                    for (int z = 0; z < schematic.length(); ++z) {
                        int worldX = xCoord + x;
                        int worldY = yCoord + y;
                        if (worldY > 255) {
                            ImpLib.logger().warn("Build height limit reached!");
                            break out;
                        }
                        int worldZ = zCoord + z;

                        Block block = schematic.getBlock(schematicPos.setPos(x, y, z));
                        IBlockState state = schematic.getBlockState(schematicPos);
                        if (block != null) {
                            if (!block.isAir(state, world, worldPos.setPos(worldX, worldY, worldZ))) {
                                world.setBlockState(worldPos, state, 2);
                                if (block.hasTileEntity(state)) {
                                    NBTTagCompound tileData = schematic.getTileData(x, y, z, worldX, worldY, worldZ);
                                    if (tileData != null) {
                                        TileEntity tile = world.getTileEntity(worldPos);
                                        if (tile == null) {
                                            ImpLib.logger().warn("Couldn't found request tileentity at " + worldX + ',' + worldY + ',' + worldZ + ", NBT tag: " + tileData);
                                            continue;
                                        }
                                        tile.readFromNBT(tileData);
                                        BlockHelper.updateBlock(world, worldPos);
                                    }
                                }
                            } else if (!world.isAirBlock(worldPos)) {
                                world.setBlockState(worldPos, BlockHelper.AIR_STATE, 2);
                                world.removeTileEntity(worldPos);
                            }
                        }
                    }
                }
            }

            int chunkX = xCoord >> 4;
            int chunkZ = zCoord >> 4;
            for (x = chunkX + (schematic.width() >> 4); chunkX < x; chunkX++) {
                for (y = chunkZ + (schematic.length() >> 4); chunkZ < y; chunkZ++) {
                    Chunk chunk = world.getChunk(chunkX, chunkZ);
                    chunk.setModified(true);
                    chunk.enqueueRelightChecks();
                }
            }
        }
    }

    public void generate(World world, BlockPos pos) {
        generate(world, pos.getX(), pos.getY(), pos.getZ());
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractStructure> T setSchematic(Schematic schematic) {
        this.schematic = schematic;
        return (T) this;
    }

    public Schematic getSchematic() {
        return this.schematic;
    }
}
