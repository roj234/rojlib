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
package ilib.asm.nixim;

import com.google.common.collect.ImmutableSetMultimap;
import ilib.asm.util.MCHooks;
import ilib.asm.util.MergedItr;
import ilib.util.MutableVec;
import org.apache.commons.lang3.mutable.MutableDouble;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.FilterList;
import roj.concurrent.OperationDone;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.world.World")
public abstract class NiximSlowMethod extends World {
    NiximSlowMethod() {
        super(null, null, null, null, false);
    }

    @Inject("func_191504_a")
    private boolean getCollisionBoxes(@Nullable Entity entityIn, AxisAlignedBB aabb, boolean canOutWorld, @Nullable List<AxisAlignedBB> outList) {
        if (outList == null)
            throw new NullPointerException("outList");

        int minX = MathHelper.floor(aabb.minX) - 1;
        int maxX = MathHelper.ceil(aabb.maxX) + 1;
        int minY = MathHelper.floor(aabb.minY) - 1;
        int maxY = MathHelper.ceil(aabb.maxY) + 1;
        int minZ = MathHelper.floor(aabb.minZ) - 1;
        int maxZ = MathHelper.ceil(aabb.maxZ) + 1;

        WorldBorder border = this.getWorldBorder();

        final boolean entityInBorder = entityIn != null && this.isInsideWorldBorder(entityIn);
        if (entityIn != null) {
            if (entityInBorder == entityIn.isOutsideBorder()) {
                entityIn.setOutsideBorder(!entityInBorder);
            }
        }

        IBlockState state = Blocks.STONE.getDefaultState();

        BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();

        if (canOutWorld && !ForgeEventFactory.gatherCollisionBoxes(this, entityIn, aabb, outList)) {
            return true;
        } else {
            if (canOutWorld) {
                if (minX < -30000000 || maxX >= 30000000 || minZ < -30000000 || maxZ >= 30000000) {
                    return true;
                }
            }
            try {
                for (int x = minX; x < maxX; ++x) {
                    for (int z = minZ; z < maxZ; ++z) {
                        final boolean xCorner = x == minX || x == maxX - 1;
                        final boolean zCorner = z == minZ || z == maxZ - 1;
                        if (!(xCorner & zCorner) && this.isBlockLoaded(mutPos.setPos(x, 64, z))) {
                            final boolean flag = !xCorner && !zCorner;
                            for (int y = minY; y < maxY; ++y) {
                                if (flag || y != maxY - 1) {
                                    mutPos.setPos(x, y, z);
                                    IBlockState state1;
                                    if (!canOutWorld && !border.contains(mutPos) && entityInBorder) {
                                        state1 = state;
                                    } else {
                                        state1 = this.getBlockState(mutPos);
                                    }

                                    state1.addCollisionBoxToList(this, mutPos, aabb, outList, entityIn, false);
                                    if (canOutWorld && !ForgeEventFactory.gatherCollisionBoxes(this, entityIn, aabb, outList)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }

                return !outList.isEmpty();
            } finally {
                mutPos.release();
            }
        }
    }

    @Inject("func_184143_b")
    public boolean collidesWithAnyBlock(AxisAlignedBB aabb) {
        int minX = MathHelper.floor(aabb.minX) - 1;
        int maxX = MathHelper.ceil(aabb.maxX) + 1;
        int minY = MathHelper.floor(aabb.minY) - 1;
        int maxY = MathHelper.ceil(aabb.maxY) + 1;
        int minZ = MathHelper.floor(aabb.minZ) - 1;
        int maxZ = MathHelper.ceil(aabb.maxZ) + 1;

        BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();

        FilterList<AxisAlignedBB> outList = MCHooks.getThrowExceptionFilter();

        if (!ForgeEventFactory.gatherCollisionBoxes(this, null, aabb, outList)) {
            return true;
        } else {
            if (minX < -30000000 || maxX >= 30000000 || minZ < -30000000 || maxZ >= 30000000) {
                return true;
            }
            try {
                for (int x = minX; x < maxX; ++x) {
                    for (int z = minZ; z < maxZ; ++z) {
                        final boolean xCorner = x == minX || x == maxX - 1;
                        final boolean zCorner = z == minZ || z == maxZ - 1;
                        if (!(xCorner & zCorner) && this.isBlockLoaded(mutPos.setPos(x, 64, z))) {
                            final boolean flag = !xCorner && !zCorner;
                            for (int y = minY; y < maxY; ++y) {
                                if (flag || y != maxY - 1) {
                                    IBlockState state1 = this.getBlockState(mutPos.setPos(x, y, z));

                                    state1.addCollisionBoxToList(this, mutPos, aabb, outList, null, false);
                                    if (!ForgeEventFactory.gatherCollisionBoxes(this, null, aabb, outList)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return false;
            } catch (OperationDone e) {
                return true;
            } finally {
                mutPos.release();
            }
        }
    }

    @Inject("func_72917_a")
    public boolean checkNoEntityCollision(AxisAlignedBB aabb, @Nullable Entity entityIn) {
        int minX = MathHelper.floor((aabb.minX - MAX_ENTITY_RADIUS) / 16);
        int maxX = MathHelper.ceil((aabb.maxX + MAX_ENTITY_RADIUS) / 16);
        int minZ = MathHelper.floor((aabb.minZ - MAX_ENTITY_RADIUS) / 16);
        int maxZ = MathHelper.ceil((aabb.maxZ + MAX_ENTITY_RADIUS) / 16);

        FilterList<Entity> list = MCHooks.getEntityAliveFilter(entityIn);

        try {
            for (int x = minX; x < maxX; ++x) {
                for (int z = minZ; z < maxZ; ++z) {
                    if (this.isChunkLoaded(x, z, true)) {
                        this.getChunk(x, z).getEntitiesWithinAABBForEntity(entityIn, aabb, list, EntitySelectors.NOT_SPECTATING);
                    }
                }
            }
        } catch (OperationDone e) {
            return false;
        }

        return true;
    }

    @Inject("func_72842_a")
    public float getBlockDensity(Vec3d vec, AxisAlignedBB bb) {
        final double dx = bb.maxX - bb.minX;
        final double dy = bb.maxY - bb.minY;
        final double dz = bb.maxZ - bb.minZ;

        double dpx = 1 / (dx * 2 + 1);
        double dpy = 1 / (dy * 2 + 1);
        double dpz = 1 / (dz * 2 + 1);

        if (dpx >= 0 && dpy >= 0 && dpz >= 0) {
            int isBlock = 0;
            int all = 0;

            double kx = (1 - Math.floor(1 / dpx) * dpx) / 2;
            double kz = (1 - Math.floor(1 / dpz) * dpz) / 2;

            MutableVec vector = new MutableVec();

            for (double xP = 0; xP <= 1; xP = (xP + dpx)) {
                for (double yP = 0; yP <= 1; yP = (yP + dpy)) {
                    for (double zP = 0; zP <= 1; zP = (zP + dpz)) {
                        double cx = bb.minX + dx * xP;
                        double cy = bb.minY + dy * yP;
                        double cz = bb.minZ + dz * zP;
                        if (this.rayTraceBlocks(vector.set(cx + kx, cy, cz + kz).getVector(), vec) == null) {
                            ++isBlock;
                        }

                        ++all;
                    }
                }
            }

            return (float) isBlock / (float) all;
        } else {
            return 0;
        }
    }

    @Override
    @Inject("getPersistentChunkIterable")
    public Iterator<Chunk> getPersistentChunkIterable(Iterator<Chunk> chunkIterator) {
        ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> persistentChunksFor = getPersistentChunks();
        Chunk[] chunks;
        int index = 0;
        if (!persistentChunksFor.isEmpty()) {
            chunks = new Chunk[persistentChunksFor.size()];
            profiler.startSection("forcedChunkLoading");
            for (ChunkPos pos : persistentChunksFor.keys()) {
                if (pos != null) {
                    chunks[index++] = getChunk(pos.x, pos.z);
                }
            }
            //profiler.endStartSection("regularChunkLoading");
            profiler.endSection();
        } else {
            if (!chunkIterator.hasNext())
                return Collections.emptyIterator();
            chunks = null;
        }

        return new MergedItr(chunks, index, chunkIterator);
    }

    @Nullable
    @Inject("func_72857_a")
    public <T extends Entity> T findNearestEntityWithinAABB(Class<? extends T> entityType, AxisAlignedBB aabb, T closestTo) {
        int minX = MathHelper.floor((aabb.minX - MAX_ENTITY_RADIUS) / 16);
        int maxX = MathHelper.ceil((aabb.maxX + MAX_ENTITY_RADIUS) / 16);
        int minZ = MathHelper.floor((aabb.minZ - MAX_ENTITY_RADIUS) / 16);
        int maxZ = MathHelper.ceil((aabb.maxZ + MAX_ENTITY_RADIUS) / 16);

        MutableDouble mutableDouble = new MutableDouble(Double.MAX_VALUE);

        FilterList<T> list = new FilterList<>(((old, latest) -> {
            if (latest != closestTo) {
                double min = mutableDouble.doubleValue();
                double curr = closestTo.getDistanceSq(latest);
                if (curr < min) {
                    mutableDouble.setValue(curr);
                    return true;
                }
            }
            return false;
        }));

        for (int x = minX; x < maxX; ++x) {
            for (int z = minZ; z < maxZ; ++z) {
                if (this.isChunkLoaded(x, z, true)) {
                    this.getChunk(x, z).getEntitiesOfTypeWithinAABB(entityType, aabb, list, EntitySelectors.NOT_SPECTATING);
                }
            }
        }

        return list.found;
    }

}