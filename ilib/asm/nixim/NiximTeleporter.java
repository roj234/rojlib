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

import ilib.util.PortalCache;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.state.pattern.BlockPattern;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/20 1:54
 */
@Nixim("net.minecraft.world.Teleporter")
public class NiximTeleporter extends Teleporter {
    public NiximTeleporter(WorldServer worldIn) {
        super(worldIn);
    }

    @Override
    @Copy
    public boolean isVanilla() {
        return false;
    }

    @Override
    @Inject("func_180620_b")
    public boolean placeInExistingPortal(Entity entityIn, float rotationYaw) {
        double minDist = -1.0D;
        boolean doCache = true;

        BlockPos target = BlockPos.ORIGIN;

        long entityChunk = ChunkPos.asLong(MathHelper.floor(entityIn.posX), MathHelper.floor(entityIn.posZ));

        //When there is vanilla cache
        PortalPosition portalPosition = this.destinationCoordinateCache.get(entityChunk);
        if (portalPosition != null) {
            minDist = 0.0D;
            target = portalPosition;
            portalPosition.lastUpdateTime = this.world.getTotalWorldTime();
            doCache = false;
        } else {
            //Otherwise, use super cache
            BlockPos center = new BlockPos(entityIn);

            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

            int minX = center.getX() - 128, minZ = center.getZ() - 128, maxX = center.getX() + 128, maxZ = center.getZ() + 128;

            for (int cx = (center.getX() >> 4) - 8, cxe = cx + 16; cx <= cxe; ++cx) {
                for (int cz = (center.getZ() >> 4) - 8, cze = cx + 16; cz <= cze; ++cz) {

                    PortalCache handler = entityIn.dimension == -1 ? PortalCache.NETHER_CACHE : PortalCache.OVERWORLD_CACHE;

                    //When chunk is not super-cached, use vanilla method and check portals
                    if (!handler.isMarked(pos.setPos(cx, 0, cz))) {
                        handler.markChunk(pos);
                        Chunk chunk = world.getChunk(cx, cz);
                        for (int dx = 0; dx < 16; ++dx) {
                            for (int dz = 0; dz < 16; ++dz) {
                                for (int y = Math.min(world.getActualHeight() - 1, chunk.getTopFilledSegment() + 16); y >= 0; --y) {
                                    if (chunk.getBlockState(dx, y, dz).getBlock() == Blocks.PORTAL) {
                                        handler.addPortal(pos.setPos((cx << 4) + dx, y, (cz << 4) + dz));

                                        if (pos.getX() >= minX
                                                && pos.getX() <= maxX
                                                && pos.getZ() >= minZ
                                                && pos.getZ() <= maxZ) {
                                            double dist = pos.distanceSq(center);
                                            if (minDist < 0.0D || dist < minDist || (dist == minDist && pos.getY() < target.getY())) {
                                                minDist = dist;
                                                target = pos;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        //Or look into the portal map
                        for (BlockPos pp : handler.getChunkPortalIterable(pos)) {
                            if (pp.getX() >= minX
                                    && pp.getX() <= maxX
                                    && pp.getZ() >= minZ
                                    && pp.getZ() <= maxZ) {
                                double dist = pp.distanceSq(center);
                                if (minDist < 0.0D || dist < minDist || (dist == minDist && pp.getY() < target.getY())) {
                                    minDist = dist;
                                    target = pp;
                                }
                            }
                        }
                    }
                }
            }

            pos.release();
        }

        if (minDist >= 0.0D) {
            if (doCache) {
                this.destinationCoordinateCache.put(entityChunk, new PortalPosition(target, this.world.getTotalWorldTime()));
            }

            double x = (double) target.getX() + 0.5D;
            double z = (double) target.getZ() + 0.5D;
            BlockPattern.PatternHelper helper = Blocks.PORTAL.createPatternHelper(this.world, target);

            final EnumFacing forwards = helper.getForwards();
            final EnumFacing.AxisDirection axisDirection = forwards.rotateY().getAxisDirection();

            boolean isNegative = axisDirection == EnumFacing.AxisDirection.NEGATIVE;

            double xz = forwards.getAxis() == EnumFacing.Axis.X ? (double) helper.getFrontTopLeft().getZ() : (double) helper.getFrontTopLeft().getX();
            double y = (double) (helper.getFrontTopLeft().getY() + 1) - entityIn.getLastPortalVec().y * (double) helper.getHeight();

            if (isNegative) {
                ++xz;
            }

            if (forwards.getAxis() == EnumFacing.Axis.X) {
                z = xz + (1.0D - entityIn.getLastPortalVec().x) * (double) helper.getWidth() * (double) axisDirection.getOffset();
            } else {
                x = xz + (1.0D - entityIn.getLastPortalVec().x) * (double) helper.getWidth() * (double) axisDirection.getOffset();
            }

            float f = 0.0F;
            float f1 = 0.0F;
            float f2 = 0.0F;
            float f3 = 0.0F;

            final EnumFacing opposite = forwards.getOpposite();
            final EnumFacing teleportDirection = entityIn.getTeleportDirection();
            if (opposite == teleportDirection) {
                f = 1.0F;
                f1 = 1.0F;
            } else if (opposite == teleportDirection.getOpposite()) {
                f = -1.0F;
                f1 = -1.0F;
            } else if (opposite == teleportDirection.rotateY()) {
                f2 = 1.0F;
                f3 = -1.0F;
            } else {
                f2 = -1.0F;
                f3 = 1.0F;
            }

            double mx = entityIn.motionX;
            double mz = entityIn.motionZ;
            entityIn.motionX = mx * (double) f + mz * (double) f3;
            entityIn.motionZ = mx * (double) f2 + mz * (double) f1;
            entityIn.rotationYaw = rotationYaw - (float) (teleportDirection.getOpposite().getHorizontalIndex() * 90) + (float) (forwards.getHorizontalIndex() * 90);

            if (entityIn instanceof EntityPlayerMP) {
                ((EntityPlayerMP) entityIn).connection.setPlayerLocation(x, y, z, entityIn.rotationYaw, entityIn.rotationPitch);
            } else {
                entityIn.setLocationAndAngles(x, y, z, entityIn.rotationYaw, entityIn.rotationPitch);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    @Inject("func_85189_a")
    public void removeStalePortalLocations(long worldTime) {
        if (worldTime % 100L == 0L) {
            long i = worldTime - 300L;

            int remain = 2000;

            ObjectIterator<PortalPosition> itr = this.destinationCoordinateCache.values().iterator();

            while (itr.hasNext() && --remain > 0) {
                Teleporter.PortalPosition pos = itr.next();
                if (pos.lastUpdateTime < i)
                    itr.remove();
            }

            PortalCache.removeStalePortalLocations(worldTime);
        }
    }
}
