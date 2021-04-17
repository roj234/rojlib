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

import com.google.common.collect.Sets;
import roj.text.CharList;
import roj.text.TextUtil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import net.minecraftforge.registries.GameData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/1 1:51
 */
public final class BlockHelper {
    public static final int PLACEBLOCK_NOTHING = 0;
    public static final int PLACEBLOCK_UPDATE = 1;
    public static final int PLACEBLOCK_SENDCHANGE = 2;
    public static final int PLACEBLOCK_NO_RERENDER = 4;
    public static final int PLACEBLOCK_RENDERMAIN = 8;
    public static final int PLACEBLOCK_NO_OBSERVER = 16;

    public static final IBlockState AIR_STATE = Blocks.AIR.getDefaultState();
    public static final ObjectIntIdentityMap<IBlockState> S2I = GameData.getBlockStateIDMap();

    public static void notifyWall(World w, BlockPos v0, BlockPos v1) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

        // 0 0 0 to 1 0 1   xOz plane D
        // 0 0 0 to 1 1 0   xOy plane D
        // 0 0 0 to 0 1 1   yOz plane D

        // 1 1 1 to 1 0 1   xOz plane U
        // 1 1 1 to 1 1 0   xOy plane U
        // 1 1 1 to 0 1 1   yOz plane U

        notify0(w, v0,
                pos1.setPos(v1.getX(), v0.getY(), v1.getZ()));
        notify0(w, v0,
                pos1.setPos(v1.getX(), v1.getY(), v0.getZ()));
        notify0(w, v0,
                pos1.setPos(v0.getX(), v1.getY(), v1.getZ()));

        notify0(w,
                pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), v1);
        notify0(w,
                pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), v1);
        notify0(w,
                pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), v1);

        pos1.release();
    }

    private static void notify0(World world, BlockPos str, BlockPos end) {
        int endX = end.getX();
        int endY = end.getY();
        int endZ = end.getZ();

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

        for (int i = str.getX(); i <= endX; i++) {
            for (int j = str.getY(); j <= endY; j++) {
                for (int k = str.getZ(); k <= endZ; k++) {
                    world.scheduleUpdate(pos.setPos(i, j, k), world.getBlockState(pos).getBlock(), 0);
                }
            }
        }
    }

    public static void fillWall(World w, IBlockState state, BlockPos v0, BlockPos v1, int flag) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

        // 0 0 0 to 1 0 1   xOz plane D
        // 0 0 0 to 1 1 0   xOy plane D
        // 0 0 0 to 0 1 1   yOz plane D

        // 1 1 1 to 1 0 1   xOz plane U
        // 1 1 1 to 1 1 0   xOy plane U
        // 1 1 1 to 0 1 1   yOz plane U

        fillBlock0(w, state, v0,
                pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), flag);
        fillBlock0(w, state, v0,
                pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), flag);
        fillBlock0(w, state, v0,
                pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), flag);

        fillBlock0(w, state,
                pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), v1, flag);
        fillBlock0(w, state,
                pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), v1, flag);
        fillBlock0(w, state,
                pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), v1, flag);

        pos1.release();
    }
    
    public static void fillVertex(World w, IBlockState state, BlockPos v0, BlockPos v1, int flag) {
        BlockPos.PooledMutableBlockPos pos0 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

        // 0 0 0 to 0 1 0, Y-Axis
        fillBlock0(w, state, v0,
                pos1.setPos(v0.getX(), v1.getY(), v0.getZ()), flag);
        // 1 0 0 to 1 1 0
        fillBlock0(w, state,
                pos0.setPos(v1.getX(), v0.getY(), v0.getZ()),
                pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), flag);
        // 0 0 1 to 0 1 1
        fillBlock0(w, state,
                pos0.setPos(v0.getX(), v0.getY(), v1.getZ()),
                pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), flag);
        // 1 0 1 to 1 1 1
        fillBlock0(w, state,
                pos0.setPos(v1.getX(), v0.getY(), v1.getZ()),
                v1, flag);

        // 0 0 0 to 1 0 0, X-Axis
        fillBlock0(w, state, v0,
                pos1.setPos(v1.getX(), v0.getY(), v0.getZ()), flag);
        // 0 0 1 to 1 0 1
        fillBlock0(w, state,
                pos0.setPos(v0.getX(), v0.getY(), v1.getZ()),
                pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), flag);
        // 0 1 0 to 1 1 0
        fillBlock0(w, state,
                pos0.setPos(v0.getX(), v1.getY(), v0.getZ()),
                pos1.setPos(v1.getX(), v1.getY(), v0.getZ()), flag);
        // 0 1 1 to 1 1 1
        fillBlock0(w, state,
                pos0.setPos(v0.getX(), v1.getY(), v1.getZ()),
                v1, flag);

        // 0 0 0 to 0 0 1, Z-Axis
        fillBlock0(w, state, v0,
                pos1.setPos(v0.getX(), v0.getY(), v1.getZ()), flag);
        // 1 0 0 to 1 0 1
        fillBlock0(w, state,
                pos0.setPos(v1.getX(), v0.getY(), v0.getZ()),
                pos1.setPos(v1.getX(), v0.getY(), v1.getZ()), flag);
        // 1 1 0 to 1 1 1
        fillBlock0(w, state,
                pos0.setPos(v1.getX(), v1.getY(), v0.getZ()),
                v1, flag);
        // 0 1 0 to 0 1 1
        fillBlock0(w, state,
                pos0.setPos(v0.getX(), v1.getY(), v0.getZ()),
                pos1.setPos(v0.getX(), v1.getY(), v1.getZ()), flag);

        pos0.release();
        pos1.release();
    }

    public static class MutableBlockPos extends BlockPos.MutableBlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(BlockPos pos) {
            super(pos.getX(), pos.getY(), pos.getZ());
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos add1(Vec3i vec3i) {
            this.x += vec3i.getX();
            this.y += vec3i.getY();
            this.z += vec3i.getZ();
            return this;
        }

        public MutableBlockPos add1(int x, int y, int z) {
            this.x += x;
            this.y += y;
            this.z += z;
            return this;
        }

        public MutableBlockPos add1(double x, double y, double z) {
            return add1((int) x, (int) y, (int) z);
        }
    }

    /**
     * 画直线(近似)
     *
     * @param x1 x起始
     * @param y1 y起始
     * @param z1 z起始
     * @param x2 x结束
     * @param y2 y结束
     * @param z2 z结束
     * @return 点集Iterator
     */
    public static Iterable<BlockPos> bresenham(double x1, double y1, double z1, double x2, double y2, double z2) {
        return () -> new BresenhamIterator(x1, y1, z1, x2, y2, z2);
    }

    public static final class BresenhamIterator implements Iterator<BlockPos> {
        public double p_x, p_y, p_z;
        public final double s_x, s_y, s_z;
        public final int len;
        int i;

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        public BresenhamIterator(double x1, double y1, double z1, double x2, double y2, double z2) {
            p_x = x1;
            p_y = y1;
            p_z = z1;

            double d_x = x2 - x1;
            double d_y = y2 - y1;
            double d_z = z2 - z1;

            len = (int) Math.ceil(Math.max(Math.abs(d_x), Math.max(Math.abs(d_y), Math.abs(d_z))));

            s_x = d_x / len;
            s_y = d_y / len;
            s_z = d_z / len;
        }

        @Override
        public boolean hasNext() {
            return i < len;
        }

        @Override
        public BlockPos next() {
            pos.setPos(p_x, p_y, p_z);
            p_x += s_x;
            p_y += s_y;
            p_z += s_z;
            i++;
            return pos;
        }
    }

    /**
     * 最大固体方块高度
     */
    public static int getSolidBlockY(World world, int x, int z) {
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
        int y = world.getChunk(pos).getTopFilledSegment() + 16;

        IBlockState state;
        Block block;
        do {
            if (--y < 0) {
                break;
            }
            state = world.getBlockState(pos.setPos(x, y, z));
            block = state.getBlock();
        }
        while (block.isAir(state, world, pos) || block.isReplaceable(world, pos) || block.isLeaves(state, world, pos) || block.isFoliage(world, pos) || block.canBeReplacedByLeaves(state, world, pos) || block instanceof BlockLiquid);
        pos.release();
        return y;
    }

    /**
     * 最大可替换方块高度
     */
    public static int getSurfaceBlockY(World world, int x, int z) {
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
        int y = world.getChunk(pos).getTopFilledSegment() + 16;

        IBlockState state;
        Block block;
        do {
            if (--y < 0) {
                break;
            }
            state = world.getBlockState(pos.setPos(x, y, z));
            block = state.getBlock();
        }
        while (block.isAir(state, world, pos) || block.isReplaceable(world, pos) || block.isLeaves(state, world, pos) || block.isFoliage(world, pos) || block.canBeReplacedByLeaves(state, world, pos));
        pos.release();
        return y;
    }

    /**
     * 最大块高度
     */
    public static int getTopBlockY(World world, int x, int z) {
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
        int y = world.getChunk(pos.setPos(x, 0, z)).getTopFilledSegment() + 16;

        IBlockState state;
        do {
            if (--y < 0) {
                break;
            }

            pos.setY(y);

            state = world.getBlockState(pos);
        } while (state.getBlock().isAir(state, world, pos));
        pos.release();
        return y;
    }

    /**
     * 获取非空气方块
     */
    public static List<BlockPos> getBlockAround(int size, EnumFacing facing, BlockPos pos, World world) {

        BlockPos.PooledMutableBlockPos pos1;
        BlockPos.PooledMutableBlockPos pos2;
        List<BlockPos> actualList = new ArrayList<>();

        if (facing.getAxis().isHorizontal()) {
            if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
                pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX() + size, pos.getY() + size, pos.getZ());
                pos2 = BlockPos.PooledMutableBlockPos.retain(pos.getX() - size, pos.getY() - size, pos.getZ());
            } else {
                pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX(), pos.getY() + size, pos.getZ() + size);
                pos2 = BlockPos.PooledMutableBlockPos.retain(pos.getX(), pos.getY() - size, pos.getZ() - size);
            }

            while (pos2.getY() < pos.getY() - 1) {
                pos1.offset(EnumFacing.UP);
                pos2.offset(EnumFacing.UP);
            }
        } else {
            pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX() + size, pos.getY(), pos.getZ() + size);
            pos2 = BlockPos.PooledMutableBlockPos.retain(pos.getX() - size, pos.getY(), pos.getZ() - size);
        }

        for (BlockPos.MutableBlockPos blockPos : BlockPos.getAllInBoxMutable(pos1, pos2)) {
            if (!world.isAirBlock(blockPos))
                actualList.add(blockPos.toImmutable());
        }

        pos1.release();
        pos2.release();

        return actualList;
    }

    public static int getBlockCountAround(int size, BlockPos pos, World world, Block block) {
        BlockPos pos1 = pos.offset(EnumFacing.UP, size).offset(EnumFacing.NORTH, size).offset(EnumFacing.WEST, size);
        BlockPos pos2 = pos.offset(EnumFacing.DOWN, size).offset(EnumFacing.SOUTH, size).offset(EnumFacing.EAST, size);

        int i = 0;
        for (BlockPos bp : BlockPos.getAllInBoxMutable(pos1, pos2)) {
            if (world.getBlockState(bp).getBlock() == block)
                i++;
        }
        return i;
    }

    public static void fillBlockIfLoaded(World world, IBlockState state, BlockPos str, BlockPos end) {
        fillBlock0(world, state, str, end, PLACEBLOCK_NO_OBSERVER | PLACEBLOCK_SENDCHANGE);
    }

    public static void fillBlock(World world, IBlockState state, BlockPos str, BlockPos end) {
        fillBlock0(world, state, str, end, PLACEBLOCK_SENDCHANGE);
    }

    public static void fillBlock0(World world, IBlockState state, BlockPos str, BlockPos end, int type) {
        int endX = end.getX();
        int endY = end.getY();
        int endZ = end.getZ();

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(0, 0, 0);

        for (int i = str.getX(); i <= endX; i++) {
            for (int j = str.getY(); j <= endY; j++) {
                for (int k = str.getZ(); k <= endZ; k++) {
                    world.setBlockState(pos.setPos(i, j, k), state, type);
                    //markBlockForUpdate(world, pos);
                }
            }
        }
        pos.release();
    }

    public static IBlockState getAdjacentBlock(World world, BlockPos pos, EnumFacing dir) {
        pos = pos.offset(dir);
        return world == null || !world.isBlockLoaded(pos) ? AIR_STATE : world.getBlockState(pos);
    }


    /* TILE ENTITY RETRIEVAL */
    public static TileEntity getAdjacentTileEntity(World world, BlockPos pos, EnumFacing dir) {
        pos = pos.offset(dir);
        return world == null || !world.isBlockLoaded(pos) ? null : world.getTileEntity(pos);
    }

    public static TileEntity getAdjacentTileEntity(TileEntity refTile, EnumFacing dir) {
        return refTile == null ? null : getAdjacentTileEntity(refTile.getWorld(), refTile.getPos(), dir);
    }

    /* BLOCK UPDATES */
    public static void callBlockUpdate(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, PLACEBLOCK_UPDATE | PLACEBLOCK_SENDCHANGE);
    }

    public static void updateBlockState(World world, BlockPos pos) {
        if (!world.isRemote) {
            WorldServer worldServer = ((WorldServer) world);
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            PlayerChunkMapEntry entry = ((WorldServer) world).getPlayerChunkMap().getEntry(cx, cz);
            if (entry != null)
                entry.sendPacket(new SPacketBlockChange(worldServer, pos));
        } else {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    public static void updateBlockData(@Nonnull TileEntity tile) {
        World world = tile.getWorld();
        if (!world.isRemote) {
            BlockPos pos = tile.getPos();
            WorldServer worldServer = ((WorldServer) world);
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            PlayerChunkMapEntry entry = ((WorldServer) world).getPlayerChunkMap().getEntry(cx, cz);
            SPacketUpdateTileEntity packet = tile.getUpdatePacket();
            if (entry != null && packet != null)
                entry.sendPacket(packet);
        }
    }

    public static void updateBlock(World world, BlockPos pos) {
        if (!world.isRemote) {
            ((WorldServer) world).getPlayerChunkMap().markBlockForUpdate(pos);
        } else {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    public void callNeighborStateChange(World world, BlockPos pos) {
        world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), false);
    }

    public void callNeighborTileChange(World world, BlockPos pos) {
        world.updateComparatorOutputLevel(pos, world.getBlockState(pos).getBlock());
    }

    /* BLOCK WALKER */
    public interface IWalker {
        boolean walk(BlockPos pos);

        Set<BlockPos> getWalkResult();

        void setParam(Object obj);
    }

    @Deprecated
    public static abstract class BlockWalker implements IWalker {
        public Object obj;

        public final Set<BlockPos> unTravel = Sets.newHashSet();
        final Set<BlockPos> traveled = Sets.newHashSet();
        public final Set<BlockPos> toTravel = Sets.newHashSet();

        public boolean walk(BlockPos startPos) {
            long startTime = System.currentTimeMillis();
            unTravel.clear();
            traveled.clear();
            toTravel.clear();

            unTravel.add(startPos);
            addNear(unTravel, startPos);

            int i = 0;
            while (canWalk(i)) {
                i++;
                removeTraveled();
                if (unTravel.isEmpty()) {
                    return true;
                }
                for (BlockPos pos : unTravel) {
                    if (isValidPos(pos)) {
                        addNear(toTravel, pos);
                    }
                }
                unTravel.addAll(toTravel);
                toTravel.clear();
            }
            return canWalk(i);
        }

        public Set<BlockPos> getWalkResult() {
            //MI.logger().debug("[BlockWalker]" + this.traveled.size() + " blocks walked");
            //MI.logger().debug("[BlockWalker] Time cost: " + (System.currentTimeMillis() - this.startTime));
            return this.traveled;
        }

        public void setParam(Object obj) {
            this.obj = obj;
        }

        void removeTraveled() {
            unTravel.removeIf(pos -> !traveled.add(pos));
        }

        protected void v(Set<BlockPos> list, BlockPos pos) {
            if (isValidPos(pos)) list.add(pos);
        }

        public void addNear(Set<BlockPos> list, BlockPos pos) {
            v(list, pos.up());
            v(list, pos.down());
            v(list, pos.east());
            v(list, pos.west());
            v(list, pos.south());
            v(list, pos.north());
        }

        public abstract boolean canWalk(int cycle);

        public abstract boolean isValidPos(BlockPos pos);
    }

    /**
     * minecraft:stone@0
     * minecraft:stone@varient=233
     */
    public static IBlockState stateFromText(String id) {
        int i = id.indexOf('@');
        String blockId = i == -1 ? id : id.substring(0, i);

        ResourceLocation loc = new ResourceLocation(blockId);
        if (!Block.REGISTRY.containsKey(loc)) {
            throw new IllegalArgumentException("Block " + blockId + " not found ");
        } else {
            Block block = Block.REGISTRY.getObject(loc);
            return i == -1 ? block.getDefaultState() : matchState(block, id.substring(i + 1));
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    public static IBlockState matchState(Block block, String desc) {
        try {
            int meta = Integer.parseInt(desc);
            if (meta < 0) {
                throw new IllegalArgumentException("meta < 0 : " + desc);
            } else {
                return block.getStateFromMeta(meta);
            }
        } catch (NumberFormatException e) {
            IBlockState state = block.getDefaultState();
            if (!"default".equals(desc)) {
                BlockStateContainer container = block.getBlockState();
                ArrayList<String> tmp = new ArrayList<>();
                CharList cl = new CharList();

                for (String one : TextUtil.split(new ArrayList<>(), cl, desc, ',')) {
                    TextUtil.split(tmp, cl, desc, '=', 2);
                    if (tmp.size() < 2) return null;

                    IProperty prop = container.getProperty(tmp.get(0));
                    if (prop == null) {
                        throw new IllegalArgumentException("Property " + tmp.get(0) + " not found ");
                    }

                    Comparable val = (Comparable) prop.parseValue(tmp.get(1)).orNull();
                    if (val == null) {
                        throw new IllegalArgumentException("Value " + tmp.get(1) + " for property " + tmp.get(0) + " not found ");
                    }

                    state = state.withProperty(prop, val);
                }

            }
            return state;
        }
    }
}