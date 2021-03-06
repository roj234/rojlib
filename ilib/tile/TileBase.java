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

package ilib.tile;

import ilib.util.BlockHelper;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/6/15 16:19
 */
public abstract class TileBase extends TileEntity {
    protected EntityPlayer lastInteract = null;

    public TileBase() {}

    // region tick

    protected boolean canTick() {
        return true;
    }

    public void func_73660_a() {
        update();
    }

    public void update() {
        if (canTick()) {
            onTick();
            if (!world.isRemote) {
                onServerTick();
            }
        }

        baseTick();
    }

    public final void sendDataUpdate() {
        BlockHelper.sendTileUpdate(this);
    }

    protected void onTick() {}

    protected void onServerTick() {}

    protected void baseTick() {}

    // endregion
    // region sync helper

    public boolean func_70300_a(EntityPlayer player) {
        return isUsableByPlayer(player);
    }

    public boolean isUsableByPlayer(@Nonnull EntityPlayer player) {
        if (getWorld().getTileEntity(this.pos) != this) {
            return false;
        }

        this.lastInteract = player;

        final double MAXIMUM_DISTANCE_SQ = 8.0 * 8.0;

        return player.getDistanceSq(pos.getX() + 0.5,
                pos.getY() + 0.5, pos.getZ() + 0.5) < MAXIMUM_DISTANCE_SQ;
    }

    public void clientReload() {
        BlockHelper.updateBlock(getWorld(), getPos());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.removeTag("id");
        return tag;
    }

    @Override
    public final SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public final void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    protected final void handleUpdateTagFallback(NBTTagCompound tag) {
        NBTTagCompound tag1 = writeToNBT(new NBTTagCompound());
        for(String key : tag1.getKeySet()) {
            if (!tag.hasKey(key))
                tag.setTag(key, tag1.getTag(key));
        }
        this.readFromNBT(tag);
    }

    // endregion
    // fastUtil

    @Override
    @SideOnly(Side.CLIENT)
    public void setPos(BlockPos posIn) {
        super.setPos(posIn);
        cacheAB = null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateContainingBlockInfo() {
        super.updateContainingBlockInfo();
        cacheAB = null;
    }

    @SideOnly(Side.CLIENT)
    private AxisAlignedBB cacheAB;

    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (cacheAB != null) return cacheAB;

        Block type = this.getBlockType();
        BlockPos pos = this.getPos();
        if (type != null) {
            try {
                return cacheAB = world.getBlockState(pos).getCollisionBoundingBox(world, pos).offset(pos);
            } catch (Throwable e) {
                return cacheAB = new AxisAlignedBB(pos.add(-1, 0, -1), this.getPos().add(1, 1, 1));
            }
        }

        return cacheAB = new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(1, 1, 1));
    }

    public boolean canRenderBreaking() {
        return false;
    }

    public boolean restrictNBTCopy() {
        return false;
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
