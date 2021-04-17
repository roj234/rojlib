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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class ItemRightClickBlock extends ItemRightClick {
    @Override
    protected ItemStack onRightClick(World world, EntityPlayer player, ItemStack stack, EnumHand hand) {
        RayTraceResult mop = this.rayTrace(world, player, false);

        if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }

        BlockPos clickPos = mop.getBlockPos();

        if (world.isBlockModifiable(player, clickPos)) {
            BlockPos targetPos = clickPos.offset(mop.sideHit);

            if (player.canPlayerEdit(clickPos, mop.sideHit, stack)) {
                return onRightClick(world, targetPos, clickPos, player, stack, mop.sideHit);
            }
        }

        return null;
    }

    protected abstract ItemStack onRightClick(World world, BlockPos targetPos, BlockPos clickPos, EntityPlayer player, ItemStack stack, EnumFacing sideHit);
}
