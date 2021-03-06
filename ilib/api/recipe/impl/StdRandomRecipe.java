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

import ilib.api.RandomEntry;
import ilib.api.recipe.IRandomRecipe;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class StdRandomRecipe extends StdItemRecipe implements IRandomRecipe<StdRandomRecipe> {
    List<RandomEntry> randoms = new ArrayList<>();

    public StdRandomRecipe(String name, int me, int tick, boolean shaped) {
        super(name, me, tick, shaped);
    }

    public StdRandomRecipe(String name, int me, int tick, boolean shaped, List<ItemStack> input, List<ItemStack> output) {
        super(name, me, tick, shaped);
        this.input = input;
    }

    @Override
    public int getCount(int id, ItemStack stack) {
        if (id >= randoms.size()) {
            throw new IllegalArgumentException("[MI??????-" + getName() + "]????????????????????????! ??????: " + id + ", ??????: " + randoms.size());
        }
        RandomEntry entry = randoms.get(id);
        if (entry == null)
            return input.get(id).getCount();
        return entry.get();
    }

    @Override
    public int getMin(int id) {
        if (id >= randoms.size()) {
            throw new IllegalArgumentException("[MI??????-" + getName() + "]????????????????????????! ??????: " + id + ", ??????: " + randoms.size());
        }
        RandomEntry entry = randoms.get(id);
        if (entry == null)
            return input.get(id).getCount();
        return entry.min;
    }

    public StdRandomRecipe addNormal() {
        randoms.add(null);
        return this;
    }

    public StdRandomRecipe addRandom(int min, int max, double... factor) {
        randoms.add(new RandomEntry(min, max, factor));
        return this;
    }

    @Override
    public boolean isRandomized() {
        return randoms != null;
    }
}