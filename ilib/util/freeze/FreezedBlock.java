/*
 * This file is a part of MoreItems
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
package ilib.util.freeze;

import ilib.util.ForgeUtil;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 冻结的方块
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/26 19:36
 */
public final class FreezedBlock extends Block {
    static final PropertyInteger META;

    static {
        PropertyInteger m;
        if(ForgeUtil.findModById("moreid") != null) {
            try {
                m = PropertyInteger.create("meta", 0, 1 << Class.forName("moreid.Config").getDeclaredField("blockStateBits").getInt(null));
            } catch (Throwable e) {
                m = PropertyInteger.create("meta", 0, 16);
            }
        } else {
            m = PropertyInteger.create("meta", 0, 16);
        }
        META = m;
    }

    public FreezedBlock() {
        super(Material.BARRIER);
    }

    @Override
    public void harvestBlock(World worldIn, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack stack) {
        if(te != null && worldIn.getTileEntity(pos) == null)
            worldIn.addTileEntity(te);
        worldIn.setBlockState(pos, state);
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        worldIn.setBlockState(pos, state);
    }

    @Override
    public String getTranslationKey() {
        return "tile.implib.freezed";
    }

    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return null;
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(META, meta);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(META);
    }

    @Override
    public int hashCode() {
        return getRegistryName() == null ? 0 : getRegistryName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FreezedBlock && ((FreezedBlock) obj).getRegistryName().equals(getRegistryName());
    }
}
