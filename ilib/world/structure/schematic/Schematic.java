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

package ilib.world.structure.schematic;

import ilib.ImpLib;
import ilib.util.BlockHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import roj.collect.LongMap;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class Schematic {
    protected final short width, height, length;

    protected byte[] data;
    protected Block[] blocks;

    protected final LongMap<NBTTagCompound> tiles;
    protected final List<NBTTagCompound> entities;

    protected Schematic(short width, short height, short length) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.entities = null;
        this.tiles = new LongMap<>();
    }

    protected Schematic(NBTTagList entities, NBTTagList tileEntities, short width, short height, short length, byte[] data, Block[] blocks) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.data = data;
        this.blocks = blocks;

        if (entities != null) {
            this.entities = Arrays.asList(new NBTTagCompound[entities.tagCount()]);
            this.reloadEntityMap(entities);
        } else {
            this.entities = null;
        }
        this.tiles = new LongMap<>(tileEntities.tagCount());
        this.reloadTileMap(tileEntities);
    }

    private void reloadTileMap(NBTTagList tileEntities) {
        this.tiles.clear();

        for (int i = 0; i < tileEntities.tagCount(); ++i) {
            NBTTagCompound tag = tileEntities.getCompoundTagAt(i);
            this.tiles.put(mergeTileIndex(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")), tag);
        }
    }

    private void reloadEntityMap(NBTTagList entities) {
        for (int i = 0; i < entities.tagCount(); ++i) {
            NBTTagCompound tag = entities.getCompoundTagAt(i);
            this.entities.set(i, tag);
        }
    }

    public Block getBlock(int x, int y, int z) {
        return this.blocks[this.getIndexFromCoordinates(x, y, z)];
    }

    public Block getBlock(BlockPos pos) {
        return getBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public int getMetadata(int x, int y, int z) {
        return this.data[this.getIndexFromCoordinates(x, y, z)] & 0xff;
    }

    public int getMetadata(BlockPos pos) {
        return getMetadata(pos.getX(), pos.getY(), pos.getZ());
    }

    public IBlockState getBlockState(BlockPos pos) {
        try {
            return getBlock(pos).getStateFromMeta(getMetadata(pos));
        } catch (Exception e) {
            ImpLib.logger().catching(new RuntimeException("Could not get block state at " + pos, e));
        }
        return BlockHelper.AIR_STATE;
    }

    public NBTTagCompound getTileData(int x, int y, int z, int worldX, int worldY, int worldZ) {
        NBTTagCompound tag = this.getTileData(x, y, z);
        if (tag != null) {
            tag.setInteger("x", worldX);
            tag.setInteger("y", worldY);
            tag.setInteger("z", worldZ);
            return tag;
        } else {
            return null;
        }
    }

    @Nullable
    public List<NBTTagCompound> getEntities() {
        return entities;
    }

    public NBTTagCompound getTileData(int x, int y, int z) {
        return this.tiles.get(mergeTileIndex(x, y, z));
    }

    public int length() {
        return this.length & 0xffff;
    }

    public int width() {
        return this.width & 0xffff;
    }

    public int height() {
        return this.height & 0xffff;
    }

    protected static long mergeTileIndex(int x, int y, int z) {
        return (long) x << 32 | (long) y << 16 | (long) z;
    }

    protected final int getIndexFromCoordinates(int x, int y, int z) {
        return y * this.width * this.length + z * this.width + x;
    }
}
