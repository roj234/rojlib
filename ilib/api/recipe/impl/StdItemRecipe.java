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
package ilib.api.recipe.impl;

import ilib.api.recipe.IRecipe;
import roj.collect.LongBitSet;
import roj.util.Helpers;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StdItemRecipe extends AbstractItemRecipe implements IRecipe {
    public List<ItemStack> input = new ArrayList<>();

    public StdItemRecipe(String name, int mePerTick, int tickCost, boolean shaped) {
        super(name, mePerTick, tickCost, shaped);
    }

    public StdItemRecipe(String name, int me, int tick, boolean shaped, List<ItemStack> input, List<ItemStack> output) {
        super(name, me, tick, shaped, output);
        this.input = input;
    }

    /**
     * @param stack null 强制为空 count=0 不消耗
     */
    public <T extends StdItemRecipe> T addInput(ItemStack stack) {
        if (stack == null) {
            if (!isShaped()) {
                throw new IllegalStateException(getName() + " - 无序合成不支持留空");
            }
            this.input.add(null);
            return Helpers.cast(this);
        }
        if (stack.isEmpty()) {
            stack = stack.copy();
            stack.setCount(1);
            if (stack.isEmpty())
                throw new NullPointerException(getName() + " - Non-consume stack is really empty");
            if (keepInputIds == null) {
                keepInputIds = new LongBitSet(this.input.size() + 1);
            }
            keepInputIds.add(this.input.size());
        }
        this.input.add(stack);
        return Helpers.cast(this);
    }

    @Override
    public List<ItemStack> getInput() {
        return this.input;
    }
}
