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

import ilib.asm.util.MCHooks;
import ilib.util.PlayerUtil;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.EnumSet;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.world.World")
public abstract class NiximCreateBlockPos extends World {
    protected NiximCreateBlockPos(ISaveHandler p_i45749_1_, WorldInfo p_i45749_2_, WorldProvider p_i45749_3_, Profiler p_i45749_4_, boolean p_i45749_5_) {
        super(p_i45749_1_, p_i45749_2_, p_i45749_3_, p_i45749_4_, p_i45749_5_);
    }

    @Shadow("field_72994_J")
    int[] lightUpdateBlockList;

    @Shadow("func_175696_F")
    private boolean isWater(BlockPos pos) {
        return false;
    }

    @Shadow("func_175663_a")
    private boolean isAreaLoaded(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd, boolean allowEmpty) {
        return false;
    }

    @Shadow("func_175638_a")
    private int getRawLight(BlockPos pos, EnumSkyBlock lightType) {
        return 0;
    }

    @Shadow("field_73008_k")
    int skylightSubtracted;
/*
    @Nullable
    public RayTraceResult rayTraceBlocks(Vec3d start, Vec3d end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        if (!Double.isNaN(start.x) && !Double.isNaN(start.y) && !Double.isNaN(start.z)) {
            if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
                int xMax = MathHelper.floor(end.x);
                int yMax = MathHelper.floor(end.y);
                int zMax = MathHelper.floor(end.z);


                int xMin = MathHelper.floor(start.x);
                int yMin = MathHelper.floor(start.y);
                int zMin = MathHelper.floor(start.z);

                int x = xMin;
                int y = yMin;
                int z = zMin;

                BlockPos pos = new BlockPos(x, y, z);
                IBlockState state = this.getBlockState(pos);
                RayTraceResult result;

                if ((!ignoreBlockWithoutBoundingBox || state.getCollisionBoundingBox(this, pos) != Block.NULL_AABB) && state.getBlock().canCollideCheck(state, stopOnLiquid)) {
                    result = state.collisionRayTrace(this, pos, start, end);
                    if (result != null) {
                        return result;
                    }
                }

                double dx = end.x - start.x;
                double dy = end.y - start.y;
                double dz = end.z - start.z;

                result = null;
                int maxTry = 200;

                while(maxTry-- >= 0) {
                    if (x == xMax && y == yMax && z == zMax) {
                        return returnLastUncollidableBlock ? result : null;
                    }

                    boolean xNeq = true;
                    boolean yNeq = true;
                    boolean zNeq = true;

                    double xxx = 999.0D;
                    double yyy = 999.0D;
                    double zzz = 999.0D;

                    double xxx2 = 999.0D;
                    double yyy2 = 999.0D;
                    double zzz2 = 999.0D;

                    double dx = end.x - start.x;
                    double dy = end.y - start.y;
                    double dz = end.z - start.z;

                    if (xMax > x) {
                        xxx = (double)x + 1;
                    } else if (xMax < x) {
                        xxx = x;
                    } else {
                        xNeq = false;
                    }

                    if (yMax > y) {
                        yyy = (double)y + 1;
                    } else if (yMax < y) {
                        yyy = y;
                    } else {
                        yNeq = false;
                    }

                    if (zMax > z) {
                        zzz = (double)z + 1;
                    } else if (zMax < z) {
                        zzz = z;
                    } else {
                        zNeq = false;
                    }

                    if (xNeq) {
                        xxx2 = (xxx - start.x) / dx;
                    }

                    if (yNeq) {
                        yyy2 = (yyy - start.y) / dy;
                    }

                    if (zNeq) {
                        zzz2 = (zzz - start.z) / dz;
                    }

                    if (xxx2 == -0.0D) {
                        xxx2 = -1.0E-4D;
                    }

                    if (yyy2 == -0.0D) {
                        yyy2 = -1.0E-4D;
                    }

                    if (zzz2 == -0.0D) {
                        zzz2 = -1.0E-4D;
                    }

                    EnumFacing face;
                    if (xxx2 < yyy2 && xxx2 < zzz2) {
                        face = xMax > x ? EnumFacing.WEST : EnumFacing.EAST;
                        start = new Vec3d(xxx, start.y + dy * xxx2, start.z + dz * xxx2);
                    } else if (yyy2 < zzz2) {
                        face = yMax > y ? EnumFacing.DOWN : EnumFacing.UP;
                        start = new Vec3d(start.x + dx * yyy2, yyy, start.z + dz * yyy2);
                    } else {
                        face = zMax > z ? EnumFacing.NORTH : EnumFacing.SOUTH;
                        start = new Vec3d(start.x + dx * zzz2, start.y + dy * zzz2, zzz);
                    }

                    x = MathHelper.floor(start.x) - (face == EnumFacing.EAST ? 1 : 0);
                    y = MathHelper.floor(start.y) - (face == EnumFacing.UP ? 1 : 0);
                    z = MathHelper.floor(start.z) - (face == EnumFacing.SOUTH ? 1 : 0);

                    pos = new BlockPos(x, y, z);
                    state = this.getBlockState(pos);

                    if (!ignoreBlockWithoutBoundingBox || state.getMaterial() == Material.PORTAL || state.getCollisionBoundingBox(this, pos) != Block.NULL_AABB) {
                        if (state.getBlock().canCollideCheck(state, stopOnLiquid)) {
                            RayTraceResult result1 = state.collisionRayTrace(this, pos, start, end);
                            if (result1 != null) {
                                return result1;
                            }
                        } else {
                            result = new RayTraceResult(RayTraceResult.Type.MISS, start, face, pos);
                        }
                    }
                }

                return returnLastUncollidableBlock ? result : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }*/

    @Inject("func_175721_c")
    public int getLight(BlockPos pos, boolean checkNeighbors) {
        if (pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000) {
            if (checkNeighbors && this.getBlockState(pos).useNeighborBrightness()) {
                BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
                EnumFacing[] values = EnumFacing.VALUES;
                int light = 0;
                for (int i = 1; i < values.length; i++) {
                    light = Math.max(light, this.getLight(pos1.setPos(pos).move(values[i]), false));
                    if (light >= 15) {
                        pos1.release();
                        return light;
                    }
                }
                pos1.release();
                return light;
            } else if (pos.getY() < 0) {
                return 0;
            } else {
                if (pos.getY() >= 256) {
                    pos = new BlockPos(pos.getX(), 255, pos.getZ());
                }

                Chunk chunk = this.getChunk(pos);
                return chunk.getLightSubtracted(pos, this.skylightSubtracted);
            }
        } else {
            return 15;
        }
    }

    @Inject("func_175676_y")
    public int getStrongPower(BlockPos pos) {
        int red = 0;

        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
        for (EnumFacing face : EnumFacing.VALUES) {
            red = Math.max(red, this.getStrongPower(pos1.setPos(pos).move(face), face));
            if (red >= 15) {
                pos1.release();
                return red;
            }
        }
        pos1.release();

        return red;
    }

    @Inject("canBlockFreezeBody")
    public boolean canBlockFreezeBody(BlockPos pos, boolean noWaterAdj) {
        Biome biome = this.getBiome(pos);
        float f = biome.getTemperature(pos);
        if (f < 0.15F) {
            if (pos.getY() >= 0 && pos.getY() < 256 && this.getLightFor(EnumSkyBlock.BLOCK, pos) < 10) {
                BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
                IBlockState state = this.getBlockState(pos1);
                Block block = state.getBlock();
                if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && state.getValue(BlockLiquid.LEVEL) == 0) {
                    if (!noWaterAdj) {
                        pos1.release();
                        return true;
                    }

                    boolean flag = this.isWater(pos1.move(EnumFacing.WEST)) && this.isWater(pos1.setPos(pos).move(EnumFacing.EAST)) && this.isWater(pos1.setPos(pos).move(EnumFacing.NORTH)) && this.isWater(pos1.setPos(pos).move(EnumFacing.SOUTH));
                    pos1.release();
                    return !flag;
                }
                pos1.release();
            }
        }
        return false;
    }

    @Inject("func_175666_e")
    public void updateComparatorOutputLevel(BlockPos pos, Block blockIn) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
        for (EnumFacing face : EnumFacing.VALUES) {
            if (this.isBlockLoaded(pos1.setPos(pos).move(face))) {
                IBlockState state = this.getBlockState(pos1);
                Block block = state.getBlock();

                block.onNeighborChange(this, pos1, pos);
                if (block.isNormalCube(state, this, pos1)) {
                    block = this.getBlockState(pos1.move(face)).getBlock();
                    if (block.getWeakChanges(this, pos1)) {
                        block.onNeighborChange(this, pos1, pos);
                    }
                }
            }
        }
        pos1.release();
    }

    @Inject("func_175687_A")
    public int getRedstonePowerFromNeighbors(BlockPos pos) {
        int cur = 0;


        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
        for (EnumFacing face : EnumFacing.VALUES) {
            cur = Math.max(this.getRedstonePower(pos1.setPos(pos).move(face), face), cur);
            if (cur >= 15) {
                //System.out.println("getRedstonePowerFromNeighbors("+ pos +"): " + cur);
                pos1.release();
                return 15;
            }
        }

        //System.out.println("getRedstonePowerFromNeighbors("+ pos +"): " + cur);
        pos1.release();
        return cur;
    }

    @Inject("func_175640_z")
    public boolean isBlockPowered(BlockPos pos) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
        for (EnumFacing face : EnumFacing.VALUES) {
            int red = this.getRedstonePower(pos1.setPos(pos).move(face), face);
            if (red > 0) {
                pos1.release();
                return true;
            }
        }
        pos1.release();
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Inject(value = "func_175705_a", flags = roj.asm.nixim.Inject.FLAG_OPTIONAL)
    public int getLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos) {
        if (!this.provider.hasSkyLight() && type == EnumSkyBlock.SKY) {
            return 0;
        } else {
            if (pos.getY() < 0) {
                pos = new BlockPos(pos.getX(), 0, pos.getZ());
            }

            if (!this.isValid(pos)) {
                return type.defaultLightValue;
            } else if (!this.isBlockLoaded(pos)) {
                return type.defaultLightValue;
            } else if (this.getBlockState(pos).useNeighborBrightness()) {
                BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();

                EnumFacing[] facings = EnumFacing.VALUES;

                int maxLight = 0;

                for (int i = 1; i < 6; i++) {
                    int currLight = getLightFor(type, pos1.setPos(pos).move(facings[i]));
                    if (currLight > maxLight)
                        maxLight = currLight;
                    if (maxLight >= 15) {
                        pos1.release();
                        return 15;
                    }
                }

                pos1.release();
                return maxLight;
            } else {
                Chunk chunk = this.getChunk(pos);
                return chunk.getLightFor(type, pos);
            }
        }
    }

    @Inject("func_175695_a")
    public void notifyNeighborsOfStateExcept(BlockPos pos, Block blockType, EnumFacing skipSide) {
        EnumSet<EnumFacing> directions = EnumSet.allOf(EnumFacing.class);
        directions.remove(skipSide);
        if (!ForgeEventFactory.onNeighborNotify(this, pos, this.getBlockState(pos), directions, false).isCanceled()) {
            BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
            for (EnumFacing facing : directions) {
                this.neighborChanged(pos1.setPos(pos).move(facing), blockType, pos);
            }
            pos1.release();
        }
    }

    @Inject("func_190522_c")
    public void updateObservingBlocksAt(BlockPos pos, Block blockType) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
        for (EnumFacing facing : EnumFacing.VALUES) {
            this.observedNeighborChanged(pos1.setPos(pos).move(facing), blockType, pos);
        }
        pos1.release();
    }

    public void neighborChanged(BlockPos pos, final Block blockIn, BlockPos fromPos) {
        int depth = MCHooks.getStackDepth();
        if (depth > 120) {
            if (depth > 140) {
                PlayerUtil.broadcastAll("neighborChanged() StackOverflow!");

                /*getChunk(pos).setBlockState(pos, Blocks.STRUCTURE_BLOCK.getDefaultState());
                BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
                for (EnumFacing facing : EnumFacing.VALUES) {
                    try {
                        getChunk(pos1.setPos(pos).move(facing)).setBlockState(pos1, Blocks.STRUCTURE_BLOCK.getDefaultState());
                    } catch (ArrayIndexOutOfBoundsException e) {
                        if(!isValid(pos1)) {
                            continue;
                        }

                        Arrays.fill(getChunk(pos1).getBlockStorageArray(), null);
                    } catch (Throwable e) {
                        Arrays.fill(getChunk(pos1).getBlockStorageArray(), null);
                    }
                }
                pos1.release();

                FMLLog.bigWarning("Stack Overflow! Block was replaced. Errors may have been discarded.");*/

                if (depth > 180) {
                    CrashReport report = CrashReport.makeCrashReport(new StackOverflowError(), "Exception while updating neighbours");
                    CrashReportCategory category = report.makeCategory("Block being updated");
                    category.addDetail("Source block type", () -> {
                        try {
                            return String.format("ID #%d (%s // %s // %s)", Block.getIdFromBlock(blockIn), blockIn.getTranslationKey(), blockIn.getClass().getName(), blockIn.getRegistryName());
                        } catch (Throwable var2) {
                            return "ID #" + Block.getIdFromBlock(blockIn);
                        }
                    });
                    //CrashReportCategory.addBlockInfo(category, pos, state);

                    throw new ReportedException(report);
                }
            }
            return;
        }


        if (!this.isRemote) {
            IBlockState state = this.getBlockState(pos);

            try {
                state.neighborChanged(this, pos, blockIn, fromPos);
            } catch (Throwable var8) {
                CrashReport crashreport = CrashReport.makeCrashReport(var8, "Exception while updating neighbours");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being updated");
                crashreportcategory.addDetail("Source block type", () -> {
                    try {
                        return String.format("ID #%d (%s // %s // %s)", Block.getIdFromBlock(blockIn), blockIn.getTranslationKey(), blockIn.getClass().getName(), blockIn.getRegistryName());
                    } catch (Throwable var2) {
                        return "ID #" + Block.getIdFromBlock(blockIn);
                    }
                });
                CrashReportCategory.addBlockInfo(crashreportcategory, pos, state);
                throw new ReportedException(crashreport);
            }
        }

    }

    /*@Inject("func_175685_c")
    public void notifyNeighborsOfStateChange(BlockPos pos, Block blockType, boolean updateObservers) {
        EnumSet<EnumFacing> directions = EnumSet.allOf(EnumFacing.class);
        if (!ForgeEventFactory.onNeighborNotify(this, pos, this.getBlockState(pos), directions, updateObservers).isCanceled()) {

            BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
            try {
                for (EnumFacing facing : directions) {
                    this.neighborChanged(pos1.setPos(pos).move(facing), blockType, pos);
                }
            } catch (StackOverflowError error) {
                return;
            } finally {
                pos1.release();
            }

            if (updateObservers) {
                this.updateObservingBlocksAt(pos, blockType);
            }
        }
    }*/

    @Inject("func_72975_g")
    public void markBlocksDirtyVertical(int x, int z, int y1, int y2) {
        int tmp;
        if (y1 > y2) {
            tmp = y2;
            y2 = y1;
            y1 = tmp;
        }

        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
        if (this.provider.hasSkyLight()) {
            for (tmp = y1; tmp <= y2; ++tmp) {
                pos1.setY(tmp);
                this.checkLightFor(EnumSkyBlock.SKY, pos1);
            }
        }
        pos1.release();

        this.markBlockRangeForRenderUpdate(x, y1, z, x, y2, z);
    }

    @Inject("func_184141_c")
    public IBlockState getGroundAboveSeaLevel(BlockPos pos) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
        pos1.setY(getSeaLevel());

        while (!this.isAirBlock(pos1)) {
            pos1.setY(pos1.getY() + 1);
        }
        pos1.setY(pos1.getY() - 1);

        IBlockState state = this.getBlockState(pos1);
        pos1.release();
        return state;
    }

    @Inject("func_175710_j")
    public boolean canBlockSeeSky(BlockPos pos) {
        if (pos.getY() >= this.getSeaLevel()) {
            return this.canSeeSky(pos);
        } else {
            BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
            pos1.setY(getSeaLevel());

            BlockPos pos2 = pos1.toImmutable();

            if (!this.canSeeSky(pos2)) {
                pos1.release();
                return false;
            } else {
                while (pos1.getY() > pos.getY()) {
                    IBlockState state = this.getBlockState(pos1.move(EnumFacing.DOWN));
                    if (state.getBlock().getLightOpacity(state, this, pos2) > 0 && !state.getMaterial().isLiquid()) {
                        pos1.release();
                        return false;
                    }
                }

                pos1.release();
                return true;
            }
        }
    }

    @Inject("func_175672_r")
    public BlockPos getTopSolidOrLiquidBlock(BlockPos pos) {
        Chunk chunk = this.getChunk(pos);


        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
        pos1.setY(chunk.getTopFilledSegment() + 16);

        BlockPos imm;

        while (pos1.getY() >= 0) {
            IBlockState state = getBlockState(pos1.move(EnumFacing.DOWN));
            if (state.getMaterial().blocksMovement() && !state.getBlock().isLeaves(state, this, pos1) && !state.getBlock().isFoliage(this, pos1)) {
                imm = pos1.move(EnumFacing.UP).toImmutable();
                pos1.release();
                return imm;
            }
        }

        imm = pos1.toImmutable();
        pos1.release();

        return imm;
    }

    @Inject("func_147470_e")
    public boolean isFlammableWithin(AxisAlignedBB bb) {
        int xMin = MathHelper.floor(bb.minX);
        int xMax = MathHelper.ceil(bb.maxX);
        int yMin = MathHelper.floor(bb.minY);
        int yMax = MathHelper.ceil(bb.maxY);
        int zMin = MathHelper.floor(bb.minZ);
        int zMax = MathHelper.ceil(bb.maxZ);
        if (this.isAreaLoaded(xMin, yMin, zMin, xMax, yMax, zMax, true)) {
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

            for (int x = xMin; x < xMax; x++) {
                for (int y = yMin; y < yMax; ++y) {
                    for (int z = zMin; z < zMax; ++z) {
                        Block block = this.getBlockState(pos.setPos(x, y, z)).getBlock();
                        if (block == Blocks.FIRE || block == Blocks.FLOWING_LAVA || block == Blocks.LAVA) {
                            pos.release();
                            return true;
                        }

                        if (block.isBurning(this, pos)) {
                            pos.release();
                            return true;
                        }
                    }
                }
            }

            pos.release();
        }
        return false;
    }

    @Inject("func_180500_c")
    public boolean checkLightFor(EnumSkyBlock lightType, BlockPos pos) {
        if (!this.isAreaLoaded(pos, 16, false)) {
            return false;
        } else {
            int updateRange = this.isAreaLoaded(pos, 18, false) ? 17 : 15;

            this.profiler.startSection("getBrightness");

            int light = this.getLightFor(lightType, pos);
            int rawLight = this.getRawLight(pos, lightType);

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            int bufferIndex = 0;

            int x1, y1, z1;
            int realLight;
            int dx, dy, dz;

            BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();

            if (rawLight > light) {
                this.lightUpdateBlockList[bufferIndex++] = 133152;
            } else if (rawLight < light) {
                this.lightUpdateBlockList[bufferIndex++] = 133152 | light << 18;

                int index2 = 0;
                outer:
                while (true) {
                    int exceptLight;
                    do {
                        do {
                            do {
                                if (index2 >= bufferIndex) {
                                    break outer;
                                }

                                int data = this.lightUpdateBlockList[index2++];
                                x1 = (data & 63) - 32 + x;
                                y1 = (data >> 6 & 63) - 32 + y;
                                z1 = (data >> 12 & 63) - 32 + z;

                                exceptLight = data >> 18 & 15;

                                realLight = this.getLightFor(lightType, mutPos.setPos(x1, y1, z1));
                            } while (realLight != exceptLight);

                            this.setLightFor(lightType, mutPos, 0);
                        } while (exceptLight <= 0);

                        dx = MathHelper.abs(x1 - x);
                        dy = MathHelper.abs(y1 - y);
                        dz = MathHelper.abs(z1 - z);
                    } while (dx + dy + dz >= updateRange);

                    for (EnumFacing facing : EnumFacing.VALUES) {
                        int xP = x1 + facing.getXOffset();
                        int yP = y1 + facing.getYOffset();
                        int zP = z1 + facing.getZOffset();
                        IBlockState state = this.getBlockState(mutPos.setPos(xP, yP, zP));
                        int opacity = Math.max(1, state.getBlock().getLightOpacity(state, this, mutPos));
                        realLight = this.getLightFor(lightType, mutPos);
                        if (realLight == exceptLight - opacity && bufferIndex < this.lightUpdateBlockList.length) {
                            this.lightUpdateBlockList[bufferIndex++] = xP - x + 32 | yP - y + 32 << 6 | zP - z + 32 << 12 | exceptLight - opacity << 18;
                        }
                    }
                }
            }

            this.profiler.endSection();
            this.profiler.startSection("checkedPosition < toCheckCount");

            int i = 0;
            while (i < bufferIndex) {
                int data = this.lightUpdateBlockList[i++];
                x1 = (data & 63) - 32 + x;
                y1 = (data >> 6 & 63) - 32 + y;
                z1 = (data >> 12 & 63) - 32 + z;

                int light1 = this.getLightFor(lightType, mutPos.setPos(x1, y1, z1));
                realLight = this.getRawLight(mutPos, lightType);
                if (realLight != light1) {
                    this.setLightFor(lightType, mutPos, realLight);
                    if (realLight > light1) {
                        dx = Math.abs(x1 - x);
                        dy = Math.abs(y1 - y);
                        dz = Math.abs(z1 - z);
                        boolean hasMoreSpace = bufferIndex < this.lightUpdateBlockList.length - 6;
                        if (dx + dy + dz < updateRange && hasMoreSpace) {

                            int xT = x1 - x + 32;
                            int yT = y1 - y + 32;
                            int zT = z1 - z + 32;

                            for (EnumFacing face : EnumFacing.VALUES) {
                                if (this.getLightFor(lightType, mutPos.setPos(x1, y1, z1).move(face)) < realLight) {
                                    this.lightUpdateBlockList[bufferIndex++] = getData123(xT, yT, zT, face);
                                }
                            }
                        }
                    }
                }
            }

            mutPos.release();

            this.profiler.endSection();
            return true;
        }
    }

    @Copy
    private static int getData123(int xT, int yT, int zT, EnumFacing face) {
        int base = face.getAxisDirection().getOffset();

        switch (face.getAxis().ordinal()) {
            case 0:
                xT += base;
                break;
            case 1:
                yT += base;
                break;
            case 2:
                zT += base;
                break;
        }

        return xT + (yT << 6) + (zT << 12);
    }
}