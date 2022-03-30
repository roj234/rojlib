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

import ilib.math.Arena;
import ilib.math.SelectionCache;
import ilib.util.MCTexts;
import ilib.util.PlayerUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ItemSelectTool extends ItemRightClick {
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
    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    protected ItemStack onRightClick(World world, EntityPlayer player, ItemStack stack, EnumHand hand) {
        long UUID = player.getUniqueID().getMostSignificantBits();
        if (player.isSneaking()) {
            if (SelectionCache.remove(UUID) && world.isRemote) {
                PlayerUtil.sendTo(player, "command.ilib.sel.clear");
                player.playSound(SoundEvents.BLOCK_NOTE_PLING, 1, 0);
                return stack;
            }
            return null;
        }

        RayTraceResult mop = this.rayTrace(world, player, false);

        if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = mop.getBlockPos();
        Arena arena = SelectionCache.get(UUID);
        if (arena != null && pos.equals(arena.getP2())) return null;

        int count = SelectionCache.set(UUID, 2, pos).getSelectionSize();

        if (world.isRemote) {
            PlayerUtil.sendTo(player, MCTexts.format("command.ilib.sel.pos2") + ": " + pos.getX() + ',' + pos.getY() + ',' + pos.getZ());
            if (count > 0) PlayerUtil.sendTo(player, "command.ilib.sel.size", count);
            player.playSound(SoundEvents.BLOCK_NOTE_PLING, 1, 1);
        }

        return stack;
    }
}
