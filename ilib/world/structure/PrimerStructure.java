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
import ilib.api.world.IPrimeGenerator;
import ilib.math.Section;
import ilib.util.BlockHelper;
import ilib.world.structure.schematic.Schematic;
import roj.math.Rect3i;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;

/**
 * @author Roj234
 * @since  2020/8/24 12:06
 */
public final class PrimerStructure implements IPrimeGenerator {
    private Schematic toGeneration;
    private final Section box;

    private final Section chunkBox = new Section(0, 0, 0, 0, 0, 0);
    private final BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

    public PrimerStructure(Schematic toGeneration, int sx, int sy, int sz) {
        this.toGeneration = toGeneration;
        this.box = new Section(sx, sy, sz, sx + toGeneration.width(), sy + toGeneration.height(), sz + toGeneration.length());
    }

    public PrimerStructure setSchematicAndRoot(Schematic schematic, int sx, int sy, int sz) {
        this.toGeneration = schematic;
        this.box.set(sx, sy, sz, sx + toGeneration.width(), sy + toGeneration.height(), sz + toGeneration.length());
        return this;
    }

    public boolean tryGenerateAt(ChunkPrimer primer, int chunkX, int chunkZ) {
        int cxs = chunkX << 4;
        int czs = chunkZ << 4;

        Rect3i sect = box.intersectsWith(chunkBox.set(cxs, 0, czs, cxs + 16, 255, czs + 16));

        if (sect != null && sect.volume() > 1) {
            int sx = sect.xmin;
            final int syStr = sect.ymin;
            final int szStr = sect.zmin;

            final int sxEnd = sect.xmax;
            final int syEnd = sect.ymax;
            final int szEnd = sect.zmax;

            final int czStr = sect.zmin - czs;

            for (int cx = sect.xmin - cxs; sx < sxEnd; sx++, cx++) {
                for (int cy = syStr, sy = 0; cy < syEnd; cy++, sy++) {
                    for (int sz = szStr, cz = czStr; sz < szEnd; sz++, cz++) {
                        if (cy > 255) {
                            throw new RuntimeException("Build height limit reached during generating structure " + toGeneration + ", bounding box " + box);
                        }

                        Block block = toGeneration.getBlock(sx, sy, sz);
                        if (block != null) {
                            IBlockState state = block.getStateFromMeta(toGeneration.getMetadata(sx, sy, sz));
                            IBlockState cache = primer.getBlockState(cx, cy, cz);

                            if (cache != state) {
                                primer.setBlockState(cx, cy, cz, state);
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void writeTileData(World world, int cxs, int czs) {
        Chunk chunk = world.getChunk(cxs, czs);

        Rect3i sect = box.intersectsWith(chunkBox.set(cxs, 0, czs, cxs + 16, 255, czs + 16));

        if (box.intersects(chunkBox)) {
            int sx = sect.xmin - box.xmin;
            final int ys = sect.ymin - box.ymin;
            final int zs = sect.zmin - box.zmin;

            final int xe = sect.xmax - sect.xmin;
            final int ye = sect.ymax - sect.ymin;
            final int ze = sect.zmax - sect.zmin;

            for (int cx = sect.xmin; sx < xe; sx++, cx++) {
                for (int cy = ys, sy = 0; cy < ye; cy++, sy++) {
                    for (int sz = zs, cz = sect.zmin; sz < ze; sz++, cz++) {
                        Block block = toGeneration.getBlock(sx, sy, sz);
                        if (block != null) {
                            IBlockState state = block.getStateFromMeta(toGeneration.getMetadata(sx, sy, sz));
                            if (!block.isAir(state, world, worldPos.setPos(cx, cy, cz))) {
                                if (block.hasTileEntity(state)) {
                                    NBTTagCompound tileData = toGeneration.getTileData(sx, sy, sz, cx, cy, cz);
                                    if (tileData != null) {
                                        TileEntity tile = chunk.getTileEntity(worldPos, Chunk.EnumCreateEntityType.IMMEDIATE);
                                        if (tile == null) {
                                            ImpLib.logger().warn("Couldn't found request tileentity at " + sx + ',' + sy + ',' + sz + ", NBT tag: " + tileData);
                                            continue;
                                        }
                                        tile.readFromNBT(tileData);
                                        BlockHelper.updateBlock(world, worldPos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void generate(World world, int chunkX, int chunkZ, ChunkPrimer primer) {
        tryGenerateAt(primer, chunkX, chunkZ);
    }
}
