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

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import roj.collect.LongMap;

import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class SchematicToWrite extends Schematic {
    protected NBTTagList entityDataList;
    protected NBTTagList tileDataList;

    protected SchematicToWrite(short width, short height, short length) {
        super(width, height, length);
        this.blocks = new Block[width * height * length];
        this.data = new byte[width * height * length];
    }

    public void readFrom(World world, BlockPos start, boolean writeEntities) {
        this.entityDataList = new NBTTagList();
        this.tileDataList = new NBTTagList();

        int endX = start.getX() + width;
        int endY = start.getY() + height;
        int endZ = start.getZ() + length;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = start.getZ(); z < endZ; z++) {
            for (int y = start.getY(); y < endY; y++) {
                for (int x = start.getX(); x < endX; x++) {
                    IBlockState state = world.getBlockState(pos.setPos(x, y, z));

                    int index = getIndexFromCoordinates(pos, start);

                    blocks[index] = state.getBlock();
                    data[index] = (byte) state.getBlock().getMetaFromState(state);

                    TileEntity te = world.getTileEntity(pos);
                    if (te != null)
                        tiles.put(mergeTileIndex(x, y, z), te.writeToNBT(new NBTTagCompound()));
                }
            }
        }

        for (LongMap.Entry<NBTTagCompound> entry : tiles.entrySet()) {
            NBTTagCompound c = entry.getValue();

            long value = entry.getKey();

            c.setInteger("x", (int) ((value >> 32) & 0xffff));
            c.setInteger("y", (int) ((value >> 16) & 0xffff));
            c.setInteger("z", (int) (value & 0xffff));
            tileDataList.appendTag(c);
        }

        if (writeEntities) {
            List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(start.getX(), start.getY(), start.getZ(), endX, endY, endZ), (entity) -> !(entity instanceof EntityPlayer));
            for (Entity entity : entities) {
                NBTTagCompound tag = new NBTTagCompound();
                if (entity.writeToNBTAtomically(tag)) {
                    tag.setInteger("x", tag.getInteger("x") - start.getX());
                    tag.setInteger("y", tag.getInteger("y") - start.getY());
                    tag.setInteger("z", tag.getInteger("z") - start.getZ());
                    this.entityDataList.appendTag(tag);
                }
            }
        }
    }

    private int getIndexFromCoordinates(BlockPos pos, BlockPos pos2) {
        return (pos.getY() - pos2.getY()) * this.width * this.length + (pos.getZ() - pos2.getZ()) * this.width + pos.getX() - pos2.getX();
    }
}
