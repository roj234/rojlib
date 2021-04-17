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
package ilib.util.energy;

import ilib.api.energy.IMEnergy;
import ilib.api.energy.IMEnergyTube;
import ilib.capabilities.Capabilities;
import ilib.util.PlayerUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 广搜处理
 */
public class WideFirstPathFinder implements PathFinder {
    private World world;

    protected MyHashSet<PathFindResult> result = new MyHashSet<>();

    public MyHashSet<Node> unTravel = new MyHashSet<>(),
            traveled = new MyHashSet<>(),
            toTravel = new MyHashSet<>();

    public static long timeCost;
    public static int badEndCount;

    private static int maxIteratorCount = 128;
    private static int maxTubeCount = 1024;
    private static int maxTubeCountOneTick = 128;
    private static int lagLevel;

    private final Map<BlockPos, Set<PathFindResult>> cached = new MyHashMap<>();

    public static void increase() {
        if (lagLevel < 16) {
            lagLevel++;
            maxIteratorCount -= 6;
            maxTubeCount -= 32;
            maxTubeCountOneTick -= 3;
            if (lagLevel > 10) {
                PlayerUtil.broadcastAll("[MI] Energy tubes calculation costs too much time!");
            }
        }
    }

    public static void decrease() {
        if (lagLevel > 0) {
            lagLevel--;
            maxIteratorCount += 6;
            maxTubeCount += 32;
            maxTubeCountOneTick += 3;
        }
    }

    @Override
    public Set<PathFindResult> find(World world, BlockPos startPos) {
        if (cached.containsKey(startPos))
            return cached.get(startPos);
        if (!cached.isEmpty())
            PlayerUtil.broadcastAll("Cache not hit ");

        long startTime = System.currentTimeMillis();

        this.world = world;

        BlockPos.PooledMutableBlockPos selfPos = BlockPos.PooledMutableBlockPos.retain();

        Node pos1 = Node.take(startPos, Integer.MAX_VALUE);
        if (!isValidPos(selfPos, EnumFacing.UP, pos1, Integer.MAX_VALUE))
            return Collections.emptySet();
        unTravel.add(pos1);

        result.clear();

        int i = 0;
        while (i < maxIteratorCount && unTravel.size() < maxTubeCountOneTick && traveled.size() < maxTubeCount) {
            if (unTravel.isEmpty()) {
                beforeDone(startTime);
                selfPos.release();
                return result;
            }

            for (Node pos : unTravel) {
                addNear(pos, selfPos);
            }

            MyHashSet<Node> tmp = unTravel;
            tmp.clear();
            unTravel = toTravel;
            toTravel = tmp;

            traveled.deduplicate(unTravel);

            i++;
        }

        badEndCount++;

        beforeDone(startTime);
        selfPos.release();

        return result;
    }

    @Override
    public void tick() {
        this.cached.clear();
    }

    public void beforeDone(long startTime) {
        timeCost += (System.currentTimeMillis() - startTime);

        Set<PathFindResult> result = new MyHashSet<>(this.result);

        for (Node pos : traveled) {
            pos.release();
            cached.put(new BlockPos(pos.x, pos.y, pos.z), result);
            //if(Node.index >= Node.CACHE_SIZE - 1)
            //    break;
        }

        traveled.clear();
        unTravel.clear();
        world = null;
    }

    public void addNear(Node origin, BlockPos.PooledMutableBlockPos selfPos) {
        int x = origin.x;
        int y = origin.y;
        int z = origin.z;

        int q = origin.speed;

        IMEnergyTube tube = origin.tube;

        for (EnumFacing face : EnumFacing.VALUES) {
            if (tube.canConnectEnergy(face)) {
                Node neighbor = Node.take(selfPos.setPos(x, y, z).move(face), 0);
                if (traveled.contains(neighbor) || !isValidPos(selfPos, face, neighbor, q)) {
                    neighbor.release();
                } else {
                    toTravel.add(neighbor);
                }
            }
        }
    }

    public boolean isValidPos(BlockPos.PooledMutableBlockPos selfPos, EnumFacing fromSide, Node pos, int prevSpeed) {
        if (world.isBlockLoaded(selfPos.setPos(pos.x, pos.y, pos.z), false)) {
            TileEntity te = world.getChunk(selfPos).getTileEntity(selfPos, Chunk.EnumCreateEntityType.CHECK);
            if (te == null) return false;
            IMEnergy im = te.getCapability(Capabilities.MENERGY_TILE, null);
            if (im != null) {
                if (im.getEnergyType() == IMEnergy.EnergyType.TUBE) {
                    return (pos.speed = Math.min((pos.tube = (IMEnergyTube) im).transSpeed(), prevSpeed)) > 0;
                } else {
                    result.add(new PathFindResult(te, prevSpeed, fromSide.getOpposite()));
                }
            }
        }
        return false;
    }

    /**
     * @since 2020/9/17 0:52
     */
    public static class Node {
        public int x, y, z;
        public int speed;
        public IMEnergyTube tube;

        private static final int CACHE_SIZE = 256;
        private static final Node[] CACHE = new Node[CACHE_SIZE];
        private static int index = -1;

        public Node() {
        }

        public Node set(BlockPos pos, int energy) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.speed = energy;
            return this;
        }

        public Node set(int x, int y, int z, int energy) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.speed = energy;
            return this;
        }

        public int hashCode() {
            return x << 24 ^ y << 8 ^ z;
        }

        public boolean equals(Object o) {
            if (o instanceof Node) {
                Node p = ((Node) o);
                return p.x == this.x && p.y == this.y && p.z == this.z;
            }
            return false;
        }

        public String toString() {
            return "Node{" + x + ',' + y + ',' + z + '}';
        }

        public static Node take(BlockPos pos, int energy) {
            if (index >= 0)
                return CACHE[index--].set(pos, energy);
            return new Node().set(pos, energy);
        }

        public void release() {
            if (index < CACHE_SIZE - 1)
                CACHE[++index] = this;
        }
    }
}
