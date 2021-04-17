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

package ilib.api.recipe;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import roj.collect.MyHashMap;

import java.util.Map;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class HammerRecipe {
    private final IBlockState input;
    private final ItemStack output;
    private final int level;

    public static final Map<IBlockState, HammerRecipe> REGISTRY = new MyHashMap<>();

    static {
        create(Blocks.COBBLESTONE, Blocks.GRAVEL, 0);
        create(Blocks.GRAVEL, Blocks.SAND, 0);
    }

    public static void create(IBlockState in, Block out, int level) {
        new HammerRecipe(in, new ItemStack(out), level);
    }

    public static void create(Block in, Block out, int level) {
        new HammerRecipe(in.getDefaultState(), new ItemStack(out), level);
    }

    public HammerRecipe(IBlockState input, ItemStack output, int level) {
        this.input = input;
        this.output = output;
        this.level = level;
        REGISTRY.put(input, this);
    }

    public IBlockState getInput() {
        return this.input;
    }

    public int getLevel() {
        return this.level;
    }

    public ItemStack getOutput() {
        return this.output;
    }
}