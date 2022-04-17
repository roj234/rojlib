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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: FurnaceRecipe.java
 */
package ilib.api.recipe.impl;

import ilib.api.recipe.IRecipe;
import ilib.fluid.handler.IFluidProvider;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class FurnaceRecipe implements IRecipe {
    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;

    public FurnaceRecipe(ItemStack in, ItemStack out) {
        inputs = Collections.singletonList(in);
        outputs = Collections.singletonList(out);
    }

    public boolean matches(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> list) {
        return true;
    }

    @Nonnull
    @Override
    public List<ItemStack> operateInput(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> input) {
        if (IRecipe.decrStackSize(input, 0, 1).isEmpty()) {
            throw new RuntimeException("FurnaceRecipe: Invalid input");
        }
        return input;
    }

    @Override
    public boolean isShaped() {
        return true;
    }

    @Nonnull
    public String getName() {
        return "FurnaceRecipe";
    }

    public int getTimeCost() {
        return 100;
    }

    public int getPowerCost() {
        return 20;
    }

    public List<ItemStack> getInput() {
        return inputs;
    }

    public List<ItemStack> getOutput() {
        return outputs;
    }
}
