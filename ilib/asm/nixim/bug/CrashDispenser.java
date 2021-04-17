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
package ilib.asm.nixim.bug;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/16 13:23
 */
@Nixim("net.minecraft.world.World")
abstract class CrashDispenser extends World {
    protected CrashDispenser(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
        super(saveHandlerIn, info, providerIn, profilerIn, client);
    }

    @Inject("func_190527_a")
    public boolean mayPlace(Block blockIn, BlockPos pos, boolean skipCollisionCheck, EnumFacing sidePlacedOn, @Nullable Entity placer) {
        if(!this.isValid(pos) || this.isOutsideBuildHeight(pos))
            return false;

        IBlockState state = this.getBlockState(pos);
        AxisAlignedBB box = skipCollisionCheck ? null : blockIn.getDefaultState().getCollisionBoundingBox(this, pos);
        if (!(placer instanceof EntityPlayer) && ForgeEventFactory.onBlockPlace(placer, new BlockSnapshot(this, pos, blockIn.getDefaultState()), sidePlacedOn).isCanceled()) {
            return false;
        } else if (box != null && box != Block.NULL_AABB && !this.checkNoEntityCollision(box.offset(pos))) {
            return false;
        } else if (state.getMaterial() == Material.CIRCUITS && blockIn == Blocks.ANVIL) {
            return true;
        } else {
            return state.getBlock().isReplaceable(this, pos) && blockIn.canPlaceBlockOnSide(this, pos, sidePlacedOn);
        }
    }
}
