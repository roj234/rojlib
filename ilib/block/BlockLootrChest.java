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
package ilib.block;

import ilib.item.handler.IItemHandler;
import ilib.item.handler.MCInventoryMI;
import ilib.tile.TileEntityLootrChest;
import ilib.util.PlayerUtil;

import net.minecraft.block.BlockChest;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.LockCode;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/18 13:38
 */
public class BlockLootrChest extends BlockChest {
    public static IBlockState INSTANCE;

    public BlockLootrChest() {
        super(Type.BASIC);
        setSoundType(SoundType.WOOD);
        setResistance(Float.MAX_VALUE);
        setHardness(3f);
        INSTANCE = getDefaultState();
    }

    protected boolean isBelowSolidBlock(World worldIn, BlockPos pos) {
        return worldIn.getBlockState(pos.up()).doesSideBlockChestOpening(worldIn, pos.up(), EnumFacing.DOWN);
    }

    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote) {
            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof TileEntityLootrChest)) {
                return false;
            } else {
                TileEntityLootrChest chest = (TileEntityLootrChest) tile;
                if (this.isBelowSolidBlock(worldIn, pos)) {
                    return false;
                } else {
                    playerIn.displayGUIChest(chest.getInv(playerIn));
                    //playerIn.addStat(StatList.CHEST_OPENED);
                }
            }
        }
        return true;
    }

    @Nullable
    public ILockableContainer getContainer(World worldIn, BlockPos pos, boolean allowBlocking) {
        return null;
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityLootrChest();
    }

    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
    }

    public boolean hasComparatorInputOverride(IBlockState state) {
        return false;
    }

    public int getComparatorInputOverride(IBlockState blockState, World worldIn, BlockPos pos) {
        return 0;
    }

    public static class MyInv extends MCInventoryMI implements ILockableContainer {

        public MyInv(IItemHandler inv) {
            super(inv, null);
        }

        @Override
        public String getName() {
            return "tile.ilib.chest_loot";
        }

        @Override
        public boolean isLocked() {
            return false;
        }

        @Override
        public void setLockCode(LockCode code) {}

        @Override
        public LockCode getLockCode() {
            return null;
        }

        @Override
        public Container createContainer(InventoryPlayer inv, EntityPlayer player) {
            return new ContainerChest(inv, this, player);
        }

        @Override
        public String getGuiID() {
            return "minecraft:chest";
        }

        @Override
        public void closeInventory(EntityPlayer p) {
            for (int i = 0; i < getSizeInventory(); i++) {
                ItemStack stack = getStackInSlot(i);
                if (!stack.isEmpty()) {
                    PlayerUtil.giveToPlayer(p, stack);
                }
            }
        }
    }
}
