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
 * This file is randoms part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: StandardRecipe.java
 */
package ilib.api.recipe.impl;

import ilib.api.RandomEntry;
import ilib.api.recipe.IRandomRecipe;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StdRandomOreRecipe extends StdOreRecipe implements IRandomRecipe<StdRandomOreRecipe> {
    public final List<RandomEntry> randoms = new ArrayList<>();

    public StdRandomOreRecipe(String name, int me, int tick) {
        super(name, me, tick);
    }

    public StdRandomOreRecipe(String name, int me, int tick, boolean shaped) {
        super(name, me, tick, shaped);
    }

    public StdRandomOreRecipe(String name, int me, int tick, boolean shaped, List<ItemStack[]> input, List<ItemStack> output) {
        super(name, me, tick, shaped, input, output);
    }

    @Override
    public int getCount(int id) {
        if (id >= randoms.size()) {
            throw new IllegalArgumentException("[MI矿物随机-" + getName() + "]随机列表大小不对! 需求: " + id + ", 实际: " + randoms.size());
        }
        RandomEntry entry = randoms.get(id);
        if (entry == null)
            return input.get(id)[0].getCount();
        return entry.get();
    }

    @Override
    public int getMin(int id) {
        if (id >= randoms.size()) {
            throw new IllegalArgumentException("[MI矿物随机-" + getName() + "]随机列表大小不对! 需求: " + id + ", 实际: " + randoms.size());
        }
        RandomEntry entry = randoms.get(id);
        if (entry == null)
            return input.get(id)[0].getCount();
        return entry.min;
    }

    public StdRandomOreRecipe addNormal() {
        randoms.add(null);
        return this;
    }

    public StdRandomOreRecipe addRandom(int min, int max, double... factor) {
        randoms.add(new RandomEntry(min, max, factor));
        return this;
    }

    @Override
    public final boolean isRandomized() {
        return true;
    }
}