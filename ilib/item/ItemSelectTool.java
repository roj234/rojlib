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

package ilib.item;

import ilib.autoreg.AutoRegBlock;
import ilib.autoreg.AutoRegItem;
import ilib.misc.SelectionCache;
import ilib.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

@AutoRegItem(value = "ilib:select_tool", model = AutoRegBlock.ModelType.MERGED)/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ItemSelectTool extends ItemRightClickBlock {
    public static Item INSTANCE;

    public ItemSelectTool() {
        INSTANCE = this;
        setCreativeTab(CreativeTabs.TOOLS);
        setMaxStackSize(1);
    }

    @Nonnull
    @Override
    public EnumAction getItemUseAction(@Nonnull ItemStack stack) {
        return EnumAction.BLOCK;
    }

    public float getDestroySpeed(@Nonnull ItemStack p1_, @Nonnull IBlockState p2_) {
        return 0F;
    }

    @Override
    public boolean canDestroyBlockInCreative(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        return false;
    }

    @Override
    protected ItemStack onRightClick(World world, BlockPos targetPos, BlockPos clickPos, EntityPlayer player, ItemStack stack, EnumFacing sideHit) {
        if (world.isRemote) return stack;
        long UUID = player.getUniqueID().getMostSignificantBits();
        if (player.isSneaking()) {
            SelectionCache.remove(UUID);
            PlayerUtil.sendTo(player, "command.ilib.sel.clear");
        } else {
            int count = SelectionCache.set(UUID, 2, clickPos).getSelectionSize();

            PlayerUtil.sendTo(player, "command.ilib.sel.pos2");

            if (count != -1) {
                PlayerUtil.sendTo(player, "command.ilib.sel.size", count);
            }

        }

        return stack;
    }
}
