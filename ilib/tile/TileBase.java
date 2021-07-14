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

import ilib.api.tile.MetaTile;
import ilib.util.BlockHelper;
import ilib.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/15 16:19
 */
public abstract class TileBase extends TileEntity {
    private World selfWorld = null;
    protected EntityPlayer lastInteract = null;
    private int tryInitCount;

    public TileBase() {
    }

    // Define of [Tick] begin;

    protected boolean canTick() {
        return true;
    }

    public void func_73660_a() {
        update();
    }

    public void update() {
        if (!inited) {
            trySelfInit();
            return;
        }

        if (canTick()) {
            onTick();
            if (getWorld().isRemote) {
                onClientTick();
            } else {
                onServerTick();
            }
        }

        baseTick();
        if (getWorld().isRemote) {
            baseTickClient();
        } else {
            baseTickServer();
        }
    }

    /**
     * 更新外貌，不包括数据
     */
    public final void markForStateUpdate(int flags) {
        BlockHelper.updateBlockState(getWorld(), getPos());
    }

    public final void markForLightUpdate() {
        getWorld().checkLightFor(EnumSkyBlock.BLOCK, pos);
    }

    public final void markForDataUpdate() {
        BlockHelper.updateBlockData(this);
    }

    protected void onTick() {
    }

    protected void onClientTick() {
    }

    protected void onServerTick() {
    }

    protected void baseTick() {
    }

    protected void baseTickClient() {
    }

    protected void baseTickServer() {
    }

    // Define of [Tick] end;
    // Define of [Name and Data getter] begin;

    @Override
    public void addInfoToCrashReport(CrashReportCategory cat) {
        super.addInfoToCrashReport(cat);
        cat.addDetail("MI_MoreDebugData", this::getCustomDebugData);
    }

    public String getCustomDebugData() {
        return "~undefined~";
    }

    // Define of [Name and Data getter] end;
    // Define of [Auto Initialization] begin;

    private boolean inited;

    protected boolean inited() {
        return inited;
    }

    protected boolean init(int meta) {
        return true;
    }

    @Override
    public void validate() {
        super.validate();
        if (super.getWorld() != null)
            selfWorld = super.getWorld();
        if (!inited) {
            trySelfInit();
        }
    }

    private void trySelfInit() {
        if (getWorld() != null) {
            if (!getWorld().isBlockLoaded(pos, false))
                return;
            updateContainingBlockInfo();
            int meta = getBlockMetadata();
            if (meta == 0 && this instanceof MetaTile) { // stored data in tileentity (may)
                meta = ((MetaTile) this).getMeta();
            }
            trySelfInit0(meta, true);
        }
    }

    private void trySelfInit0(int meta, boolean flag) {
        inited = init(meta);
        if (!inited) {
            tryInitCount++;
            if (tryInitCount > 2) {
                PlayerUtil.broadcastAll(new TextComponentString("Block at world " + getWorld().provider.getDimension() + " position " + pos + " was broken!!!"));
                getWorld().removeTileEntity(pos);
            }
        }
    }

    // Define of [Auto Initialization] end;
    // Define of [Fixed world getter] begin;

    @Override
    public final void setWorldCreate(@Nonnull World world) {
        this.selfWorld = world;
        setWorld(world);
    }

    @Override
    @Nonnull
    public final World getWorld() {
        World w;
        return (w = super.getWorld()) != null ? w : this.selfWorld;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound tag) {
        if (this instanceof MetaTile) {
            tag.setInteger("Ty", ((MetaTile) this).getMeta());
        }
        return super.writeToNBT(tag);
    }

    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (this instanceof MetaTile) {
            trySelfInit0(tag.getInteger("Ty"), false);
        }
    }

    // Define of [Fixed world getter] end;
    // Define of [SyncHelper] begin;

    public boolean func_70300_a(EntityPlayer player) {
        return isUsableByPlayer(player);
    }

    public boolean isUsableByPlayer(@Nonnull EntityPlayer player) {
        if (getWorld().getTileEntity(this.pos) != this) {
            return false;
        }

        this.lastInteract = player;

        final double X_CENTRE_OFFSET = 0.5;
        final double Y_CENTRE_OFFSET = 0.5;
        final double Z_CENTRE_OFFSET = 0.5;
        final double MAXIMUM_DISTANCE_SQ = 8.0 * 8.0;

        return player.getDistanceSq(pos.getX() + X_CENTRE_OFFSET,
                pos.getY() + Y_CENTRE_OFFSET, pos.getZ() + Z_CENTRE_OFFSET) < MAXIMUM_DISTANCE_SQ;
    }

    public void clientReload() {
        BlockHelper.updateBlock(getWorld(), getPos());
    }

    @Nonnull
    @Override
    public final SPacketUpdateTileEntity getUpdatePacket() {
        final int METADATA = getBlockMetadata();

        return new SPacketUpdateTileEntity(getPos(), METADATA, getUpdateTag());
    }

    @Override
    public final void onDataPacket(@Nonnull NetworkManager net, @Nonnull SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    @Nonnull
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public final void handleUpdateTag(@Nonnull NBTTagCompound tag) {
        this.readFromNBT(tag);
    }

    // fastUtil

    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        Block type = this.getBlockType();
        BlockPos pos = this.getPos();
        if (type != null) {
            try {
                AxisAlignedBB cbb = this.world.getBlockState(this.getPos()).getCollisionBoundingBox(this.world, pos).offset(pos);
                cbb.contains(Vec3d.ZERO);
                return cbb;
            } catch (Throwable e) {
                return new AxisAlignedBB(this.getPos().add(-1, 0, -1), this.getPos().add(1, 1, 1));
            }
        }

        return new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(1, 1, 1));
    }

    public boolean canRenderBreaking() {
        return false;
    }

    public boolean restrictNBTCopy() {
        return false;
    }

}
