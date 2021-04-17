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

import ilib.ImpLib;
import ilib.api.energy.IMEnergy;
import ilib.capabilities.Capabilities;
import ilib.math.FastPath;
import ilib.math.PositionProvider;
import ilib.math.Section;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.IntSet;
import roj.collect.LongMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/17 0:49
 */
public class Network implements PositionProvider {
    protected FastPath<Network> fastPathStorage;

    protected final IntMap<IMEnergy> allBlocks = new IntMap<>();
    protected final Section aabb;
    protected final int id, world;

    private static final int POS_MASK = 0b00_000_111111111_111111111_111111111;

    private static final Int2IntMap transferredInTick = new Int2IntMap();

    private final IntSet edges = new IntSet(),
            providers = new IntSet();
    private final IntMap<Object> cache = new IntMap<>();
    // key: providerOffset, Val: Key: Recv Offset, Val: Recv speed

    @Override
    @SuppressWarnings("unchecked")
    public void handle(FastPath<? extends PositionProvider> fastPath) {
        this.fastPathStorage = (FastPath<Network>) fastPath;
    }

    public void transfer() {
        if (cache.isEmpty()) {
            return;
        }

        transferredInTick.clear();

        for (IntMap.Entry<Object> pEntry : cache.entrySet()) {
            EnumFacing provPos = EnumFacing.VALUES[pEntry.getKey() >>> 27];

            IMEnergy provider = allBlocks.get(pEntry.getKey() & POS_MASK);
            if (!provider.canExtract() || !provider.canConnectEnergy(provPos)) continue;

            Int2IntMap.Entry entry = transferredInTick.getEntryOrCreate(pEntry.getKey() & POS_MASK, Math.min(provider.currentME(), provider.extractSpeed()));
            if (entry.v <= 0) {
                continue;
            }

            double transRemain = entry.v;

            Object object = pEntry.getValue();

            if (object.getClass() == Int2IntMap.class) {
                Int2IntMap map = (Int2IntMap) object;

                int i = map.size();

                double eachTransmit;

                for (Int2IntMap.Entry rEntry : map.entrySet()) {
                    eachTransmit = transRemain / (double) i;

                    int c = transferEnergy(provider, rEntry.getKey(), rEntry.v, eachTransmit);

                    if (c > 0)
                        transRemain -= c;

                    if (transRemain <= 0) break;
                    i--;
                }
            } else {
                int c = transferEnergy(provider, (Integer) object, Integer.MAX_VALUE, transRemain);

                if (c > 0)
                    transRemain -= c;
            }

            if (transRemain < 0) {
                // todo warning
                throw new IllegalArgumentException();
            }

            entry.v = (int) transRemain;
        }
    }

    private int transferEnergy(IMEnergy provider, int recvOffset, int speed, double avg) {
        EnumFacing recvFace = EnumFacing.VALUES[recvOffset >>> 27];

        IMEnergy receiver = allBlocks.get(recvOffset & POS_MASK);

        if (!receiver.canReceive() || !receiver.canConnectEnergy(recvFace)) return -2;

        Int2IntMap.Entry ticklyRemain = transferredInTick.getEntryOrCreate(recvOffset & POS_MASK, -1);

        if (ticklyRemain.v == 0)
            return -1;

        if (ticklyRemain.v == -1)
            ticklyRemain.v = receiver.receiveSpeed();
        int willTransfer = ticklyRemain.v;

        if (speed < willTransfer) willTransfer = speed;
        if (avg < willTransfer) willTransfer = (int) avg;

        if (willTransfer <= 0) return -1;

        //float factor = 1.0f;
        int count = provider.extractME(willTransfer, true);

        //Math.floor(((float)count)*factor);
        count = provider.extractME(receiver.receiveME(count, false), false);

        ticklyRemain.v -= count;

        return count;
    }

    public boolean isEmpty() {
        return allBlocks.isEmpty();
    }

    @SuppressWarnings("fallthrough")
    public boolean addEntry(BlockPos pos, IMEnergy energy) {
        int offset = offset(aabb, pos);
        if (allBlocks.put(offset, energy) != null)
            throw new IllegalArgumentException(energy.toString() + " Register twice in " + this.toString());

        PlayerUtil.broadcastAll("V " + offset + " r: " + allBlocks);

        if (!edges.isEmpty() && !edges.remove(offset)) return false;

        switch (energy.getEnergyType()) {
            case PROVIDER:
            case STORAGE:
                providers.add(offset);
            case TUBE:
                addEdgeFor(pos);
        }

        clearCache();

        return true;
    }

    protected void addEdgeFor(BlockPos pos) {
        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();

        for (EnumFacing facing : EnumFacing.VALUES) {
            edges.add(offset(aabb, pos1.setPos(pos).move(facing)));
        }

        pos1.release();
    }

    private static int offset(Section aabb, BlockPos pos) {
        return ((pos.getX() - aabb.xmin + 1) << 18) | ((pos.getY() - aabb.ymin + 1) << 9) | (pos.getZ() - aabb.zmin + 1);
    }

    private BlockPos.PooledMutableBlockPos unoffset(BlockPos.PooledMutableBlockPos pos, int offset) {
        return pos.setPos(aabb.xmin + ((offset >> 18) & 0b111111111) - 1, aabb.ymin + ((offset >> 9) & 0b111111111) - 1, aabb.zmin + (offset & 0b111111111) - 1);
    }

    @Override
    public String toString() {
        return "Network{" + world + "@" + aabb + ", i=" + id + '}';
    }

    @Override
    public boolean contains(BlockPos pos) {
        return edges.contains(offset(aabb, pos));
    }

    public boolean removeEntry(BlockPos pos) {
        int offset = offset(aabb, pos);
        IMEnergy energy = allBlocks.remove(offset);
        if (energy == null) return false;

        BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();

        switch (energy.getEnergyType()) {
            case STORAGE:
            case PROVIDER:
                for (int i = 0; i < 6; i++) {
                    cache.remove(i << 27 | offset);
                }

                if (energy.getEnergyType() == IMEnergy.EnergyType.STORAGE) {
                    removeReceiver(offset);
                    clearCache();
                }

                for (EnumFacing facing : EnumFacing.VALUES) {
                    int offset1 = offset(aabb, pos1.setPos(pos).move(facing));
                    if (!allBlocks.containsKey(offset1)) {
                        PlayerUtil.broadcastAll(this + ": Remove edge for " + pos1);
                        edges.remove(offset1);
                    } else {
                        addEdgeFor(unoffset(pos1, offset1));
                    }
                }

                break;
            case RECEIVER:
                removeReceiver(offset);
                updateBorder();
                break;
            case TUBE:
                clearCache();
        }

        pos1.release();

        return true;
    }

    private void removeReceiver(int offset) {
        for (Iterator<Object> itr = cache.values().iterator(); itr.hasNext(); ) {
            Object obj = itr.next();
            if (obj.getClass() == Int2IntMap.class) {
                Int2IntMap map = (Int2IntMap) obj;
                for (int i = 0; i < 6; i++) {
                    map.remove(i << 27 | offset);
                }
            } else {
                int off = POS_MASK & (int) obj;
                if (off == offset) {
                    itr.remove();
                }
            }
        }
    }

    public void clearCache() {
        cache.clear();
        updateBorder();
    }

    public Network merge(Network network2) {
        aabb.merge(network2.aabb);
        providers.addAll(network2.providers);
        allBlocks.putAll(network2.allBlocks);
        edges.addAll(network2.edges);
        clearCache();

        network2.allBlocks.clear();
        network2.edges.clear();
        network2.providers.clear();
        network2.clearCache();

        return this;
    }

    public Network split(int id) {
        Network network2 = new Network(this.aabb.copy(), id, world);
        // todo
        return null;
    }

    protected void updateBorder() {
        if (allBlocks.isEmpty()) return;

        fastPathStorage.remove(this);

        int ox = this.aabb.xmin;
        int oy = this.aabb.ymin;
        int oz = this.aabb.zmin;

        this.aabb.set(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (PrimitiveIterator.OfInt itr = this.allBlocks.keySet().iterator(); itr.hasNext(); ) {
            int offset = itr.nextInt();

            int x = ox + ((offset >> 18) & 0b111111111) - 1;
            int y = oy + ((offset >> 9) & 0b111111111) - 1;
            int z = oz + (offset & 0b111111111) - 1;

            if (aabb.xmin > x) aabb.xmin = x;
            if (aabb.xmax < x) aabb.xmax = x;

            if (aabb.ymin > y) aabb.ymin = y;
            if (aabb.ymax < y) aabb.ymax = y;

            if (aabb.zmin > z) aabb.zmin = z;
            if (aabb.zmax < z) aabb.zmax = z;
        }

        fastPathStorage.put(this);

        PlayerUtil.broadcastAll(this + ": Border updated to " + getSection());
        /*BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
        PlayerUtil.broadcastAll("edges: ");
        for (PrimitiveIterator.OfInt itr = edges.iterator(); itr.hasNext(); ) {
            int i = itr.nextInt();
            PlayerUtil.broadcastAll(unoffset(pos, i).toString());
        }
        pos.release();*/
    }

    public void tick(PathFinder pathFinder) {
        if (!cache.isEmpty()) {
            if (!providers.isEmpty())
                transfer();
            return;
        }

        World world = DimensionHelper.getWorldForDimension(PlayerUtil.getMinecraftServer().worlds[0], this.world, false);

        if (world == null || !world.isAreaLoaded(aabb.minBlock(), aabb.maxBlock())) {
            throw new IllegalArgumentException("Block not all loaded in " + this);
        }

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
        // filter all provider
        for (PrimitiveIterator.OfInt itr = providers.iterator(); itr.hasNext(); ) {
            int off = itr.nextInt();

            for (EnumFacing facing : EnumFacing.VALUES) {
                int off1 = facing.ordinal() << 27 | off;
                TileEntity tile = world.getChunk(unoffset(pos, off).move(facing)).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
                if (tile == null)
                    continue;

                IMEnergy cap = tile.getCapability(Capabilities.MENERGY_TILE, null);
                if (cap == null)
                    continue;

                switch (cap.getEnergyType()) {
                    case TUBE:
                        Set<PathFindResult> result = pathFinder.find(world, unoffset(pos, off));
                        Int2IntMap map = new Int2IntMap(result.size());

                        for (PathFindResult pfr : result) {
                            map.put(pfr.face.ordinal() << 27 | offset(aabb, pfr.tile.getPos()), pfr.speed);
                        }
                        cache.put(off1, map);
                        break;
                    case RECEIVER:
                    case STORAGE:
                        int recvOff = facing.getOpposite().ordinal() << 27 | offset(aabb, unoffset(pos, off).move(facing));
                        cache.put(off1, recvOff);
                        break;
                }
            }

        }

        pos.release();

        transfer();
    }

    public Network(Section aabb, int id, int world) {
        this.aabb = aabb;
        this.id = id;
        this.world = world;
    }

    public byte[] write() {
        ByteWriter w = new ByteWriter(128).writeVarInt(id, false).writeVarInt(world).writeVarInt(aabb.xmin).writeVarInt(aabb.ymin).writeVarInt(aabb.zmin).writeVarInt(aabb.xmax).writeVarInt(aabb.ymax).writeVarInt(aabb.zmax).writeVarInt(allBlocks.size(), false);
        for (PrimitiveIterator.OfInt iterator = allBlocks.keySet().iterator(); iterator.hasNext(); ) {
            int key = iterator.nextInt();
            w.writeVarInt(key, false);
        }

        return w.toByteArray();
    }

    public static Primer readFrom(byte[] tag) {
        ByteReader r = new ByteReader(tag);
        int id = r.readVarInt(false);
        int world = r.readVarInt();
        Section aabb = new Section(r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readVarInt());

        Network network = new Network(aabb, id, world);

        int size = r.readVarInt(false);
        IntMap<IMEnergy> allBlocks = network.allBlocks;
        allBlocks.ensureCapacity(size);
        for (int i = 0; i < size; i++) {
            allBlocks.put(r.readVarInt(false), null);
        }

        return new Primer(network);
    }

    @Override
    public Section getSection() {
        return (Section) aabb.copy().grow(1);
    }

    @Override
    public int getWorld() {
        return world;
    }

    public void onTileLoad(BlockPos.PooledMutableBlockPos pos, TileEntity tile) {
        int off = offset(aabb, pos);

        IMEnergy cap = tile.getCapability(Capabilities.MENERGY_TILE, null);
        if (cap == null) {
            if (allBlocks.containsKey(off)) {
                invalidate(tile.getWorld());
                ImpLib.logger().error("Unannounced Block Change at " + pos);
            }
            return;
        }

        if (!allBlocks.containsKey(off)) {
            invalidate(tile.getWorld());
            ImpLib.logger().error("Unannounced Block Change at " + pos);
        }/* else {
            if(cap.getEnergyType() == IMEnergy.EnergyType.TUBE) {
                edges.add(off);
            }
        }*/
    }

    public void invalidate(World world) {
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
        for (PrimitiveIterator.OfInt iterator = allBlocks.keySet().iterator(); iterator.hasNext(); ) {
            int i = iterator.nextInt();
            TileEntity tile = world.getTileEntity(unoffset(pos, i));
            if (tile == null) continue;
            IMEnergy cap = tile.getCapability(Capabilities.MENERGY_TILE, null);
            if (cap != null) cap.onLeave();
        }
        pos.release();
        allBlocks.clear();
        providers.clear();
        clearCache();
    }

    /**
     * @since 2020/9/17 0:48
     */
    public static class Primer implements PositionProvider {
        Network network;
        LongMap<Object> chunks;

        public Primer() {
        }

        public Primer(Network network) {
            this.network = network;
            this.chunks = new LongMap<>();
            for (int x = network.aabb.xmin, xm = network.aabb.xmax; x < xm; x += 16) {
                for (int z = network.aabb.zmin, zm = network.aabb.zmax; z < zm; z += 16) {
                    chunks.put(((long) x >>> 4) << 32 | z >>> 4, null);
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Primer that = (Primer) o;
            return network.equals(that.network);
        }

        @Override
        public int hashCode() {
            return Objects.hash(network);
        }

        public boolean onChunkLoad(World world, Chunk chunk) {
            chunks.remove((long) chunk.x << 32 | chunk.z);
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
            Map<BlockPos, TileEntity> entityMap = chunk.getTileEntityMap();

            for (int x = Math.max(chunk.x << 4, network.aabb.xmin), ex = Math.min(chunk.x << 4, network.aabb.xmax); x < 16; x++) {
                for (int z = Math.max(chunk.z << 4, network.aabb.zmin), ez = Math.min(chunk.z << 4, network.aabb.zmax); z < 16; z++) {
                    for (int y = network.aabb.ymin; y < network.aabb.ymax; y++) {
                        TileEntity tile = entityMap.get(pos.setPos(x, y, z));
                        if (tile != null) {
                            network.onTileLoad(pos, tile);
                        }
                    }
                }
            }
            pos.release();

            return chunks.size() == 0;
        }

        @Override
        public Section getSection() {
            return network.aabb;
        }

        @Override
        public int getWorld() {
            return network.world;
        }
    }
}
