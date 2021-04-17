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
package ilib.asm.nixim;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/22 15:50
 */
@Nixim("net.minecraft.block.BlockSlab")
abstract class NiximSlab extends BlockSlab {
    public NiximSlab(Material materialIn) {
        super(materialIn);
    }

    @Copy
    private boolean breaking;

    @Override
    @Inject("removedByPlayer")
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        this.onBlockHarvested(world, pos, state, player);

        IBlockState state1 = Blocks.AIR.getDefaultState();
        if (isDouble()) {
            RayTraceResult result = player.rayTrace(6, 0);
            if (result != null) {
                boolean side = getSide(player, result);

                try {
                    int meta = getMetaFromState(state) & 7;

                    BlockSlab slab = (BlockSlab) ((ItemBlock) getItemDropped(state, null, 0)).getBlock();

                    breaking = true;

                    if (side) { // set bottom
                        state1 = slab.getStateFromMeta(meta);
                    } else { // set top
                        state1 = slab.getStateFromMeta(meta | 8);
                    }
                } catch (Throwable ignored) {
                    breaking = false;
                }
            }
        }

        return world.setBlockState(pos, state1, world.isRemote ? 11 : 3);
    }

    @Copy
    private static boolean getSide(EntityPlayer player, RayTraceResult result) {
        Vec3d hit = result.hitVec;

        double hitY = hit.y;
        double relativeY = hitY - (int) hitY;

        if (relativeY == 0) {
            return !(hitY >= player.posY + (double) player.getEyeHeight());
        }

        // hit at y>0.5
        else return relativeY >= 0.5;
    }

    @Inject("func_149745_a")
    public final int quantityDropped(Random random) {
        if (breaking) {
            breaking = false;
            return 1;
        }
        return this.isDouble() ? 2 : 1;
    }
}
