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

package ilib.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class BlockInfo {
    public static final BlockInfo AIR = new BlockInfo(BlockHelper.AIR_STATE);

    public IBlockState state;
    public NBTTagCompound tag;
    private boolean anyMeta; // todo

    public BlockInfo(IBlockState state, NBTTagCompound tag) {
        this(state);
        this.tag = tag;
    }

    public BlockInfo(IBlockState state) {
        this.state = state;
    }

    @Deprecated
    public BlockInfo(Block block, int meta) {
        this(block.getStateFromMeta(meta));
    }

    public static BlockInfo get(ItemStack stack) {
        return new BlockInfo(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
    }

    @Deprecated
    public static ItemStack toStack(IBlockState input) {
        return new ItemStack(input.getBlock(), 1, input.getBlock().getMetaFromState(input));
    }

    public IBlockState getState() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockInfo blockInfo = (BlockInfo) o;
        return state == blockInfo.state &&
                Objects.equals(tag, blockInfo.tag);
    }

    @Override
    public int hashCode() {
        return state.hashCode() * 31 + (tag == null ? 0 : tag.hashCode());
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "{" + state + "@" + tag + "}";
    }

    public Block getBlock() {
        return state.getBlock();
    }

    public boolean anyMeta() {
        return anyMeta;
    }

    public int getMeta() {
        return state.getBlock().getMetaFromState(state);
    }
}