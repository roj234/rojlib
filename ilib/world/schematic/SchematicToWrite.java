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

package ilib.world.schematic;

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

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class SchematicToWrite extends Schematic {
    NBTTagList entityTags, tileTags;

    public SchematicToWrite(short width, short height, short length) {
        super(width, height, length);
        this.blocks = new Block[width * height * length];
        this.data = new byte[width * height * length];
    }

    public void readFrom(World world, BlockPos start, boolean doEntities) {
        this.entityTags = new NBTTagList();
        this.tileTags = new NBTTagList();

        int endX = start.getX() + width;
        int endY = start.getY() + height;
        int endZ = start.getZ() + length;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = start.getY(); y < endY; y++) {
            for (int z = start.getZ(); z < endZ; z++) {
                for (int x = start.getX(); x < endX; x++) {
                    IBlockState state = world.getBlockState(pos.setPos(x, y, z));

                    int idx = getIndexFromCoordinates(x - start.getX(), y - start.getY(), z - start.getZ());

                    blocks[idx] = state.getBlock();
                    data[idx] = (byte) state.getBlock().getMetaFromState(state);

                    TileEntity te = world.getTileEntity(pos);
                    if (te != null) {
                        NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
                        tag.setShort("x", (short) (x - start.getX()));
                        tag.setShort("y", (short) (y - start.getY()));
                        tag.setShort("z", (short) (z - start.getZ()));
                        tileTags.appendTag(tag);
                    }
                }
            }
        }

        if (doEntities) {
            world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(start.getX(), start.getY(), start.getZ(),
                                                                        endX, endY, endZ), (entity) -> {
                if (!(entity instanceof EntityPlayer)) {
                    NBTTagCompound tag = new NBTTagCompound();
                    if (entity.writeToNBTAtomically(tag)) {
                        tag.setInteger("x", tag.getInteger("x") - start.getX());
                        tag.setInteger("y", tag.getInteger("y") - start.getY());
                        tag.setInteger("z", tag.getInteger("z") - start.getZ());
                        this.entityTags.appendTag(tag);
                    }
                }
                return false;
            });
        }
    }
}
